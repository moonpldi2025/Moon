package qilin.core.solver.csc.plugin.container;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import pascal.taie.collection.Pair;
import pascal.taie.collection.TwoKeyMap;
import qilin.core.PTA;
import qilin.core.builder.CSCCallGraphBuilder;
import qilin.core.builder.CSCMethodNodeFactory;
import qilin.core.builder.CallGraphBuilder;
import qilin.core.builder.MethodNodeFactory;
import qilin.core.pag.*;
import qilin.core.solver.csc.CutShortcutSolver;
import qilin.core.solver.csc.plugin.Plugin;
import qilin.core.solver.csc.plugin.container.HostMap.HostList;
import qilin.core.solver.csc.plugin.container.HostMap.HostSet;
import qilin.core.solver.csc.plugin.container.HostMap.HostSetFactory;
import qilin.core.solver.csc.plugin.container.element.HostPointer;
import qilin.util.PTAUtils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.queue.QueueReader;

import static qilin.core.solver.csc.plugin.container.ClassAndTypeClassifier.*;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.*;
import static qilin.util.PTAUtils.isConcerned;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ContainerAccessHandler implements Plugin {

    private PTA pta;
    private CSCPAG pag;
    private CSCCallGraphBuilder cgb;
    private final ContainerConfig containerConfig;

    private Map<ContextVarNode, HostList> csVarToHostMap;
    private final HostSetFactory hostSetFactory;
    private CutShortcutSolver solver;
    private final Map<SootClass, LocalVarNode> virtualARgsOfEntrySet = Maps.newMap();
    private final Map<SootClass, SootMethod> abstractListToGet = Maps.newMap();

    private final MultiMap<LocalVarNode, LocalVarNode> arrayVarToVirtualArrayVar = Maps.newMultiMap();
    private final MultiMap<LocalVarNode, LocalVarNode> collectionVarToVirtualArrayVar = Maps.newMultiMap();
    private final MultiMap<LocalVarNode, LocalVarNode> extenderLargerToSmaller = Maps.newMultiMap();
    private final MultiMap<LocalVarNode, LocalVarNode> extenderSmallerToLarger = Maps.newMultiMap();

    private final MultiMap<Host, Pair<LocalVarNode, InvokeExpr>> hostToExits = Maps.newMultiMap();
    private final String[] categories = new String[]{"Map-Key", "Map-Value", "Col-Value"};
    private final MultiMap<Pair<HostList.Kind, ContextVarNode>, Pair<HostList.Kind, ContextVarNode>> hostPropagater = Maps.newMultiMap();

    private final HostList.Kind[] SetKindsInMap = new HostList.Kind[]{MAP_KEY_SET, MAP_VALUES, MAP_ENTRY_SET};
    private final MultiMap<LocalVarNode, Pair<AssignStmt, String>> varToMustRelatedInvokes = Maps.newMultiMap();
    public ContainerAccessHandler(Map<InvokeExpr, Integer> invokeToLine){
        containerConfig = MakeDefaultContainerConfig.make(invokeToLine);
        hostSetFactory = new HostSetFactory(containerConfig.getHostIndexer());
    }

    @Override
    public void setSolver(CutShortcutSolver solver) {
        this.solver = solver;
        this.pta = solver.getPta();
        this.pag = solver.getPag();
        this.cgb = solver.getCgb();
        this.csVarToHostMap = solver.getCsVarTohostMap();
    }

    @Override
    public void onStart() {
        for (SootClass c : containerConfig.getAllEntrySetClasses()) {
            LocalVarNode argVar = new LocalVarNode(c + "/arg", RefType.v("java.lang.Object"), null);
            virtualARgsOfEntrySet.put(c, argVar);
        }

        containerConfig.taintAbstractListClasses().forEach(jClass -> {
            try {
                SootMethod getMethod = resolveMethod(jClass, "java.lang.Object get(int)");
                if(getMethod != null){
                    abstractListToGet.put(jClass, getMethod);
                }
            }catch (RuntimeException e){
                System.err.println("Failed to resolve taint AbstractListClasses's \"get\" method.");
            }
        });
    }

    private SootMethod resolveMethod(SootClass clz, String subSig) throws RuntimeException{
        if(clz == null) return null;
        SootMethod ret;
        try{
            ret = clz.getMethod(subSig);
        }catch (RuntimeException e){
            return resolveMethod(clz.getSuperclass(), subSig);
        }
        return ret;
    }

    @Override
    public void onNewPointsToSet(ValNode pointer, Set<AllocNode> newPts) {

        if(pointer instanceof ContextVarNode contextVarNode && contextVarNode.base() instanceof LocalVarNode var){
            HostSet colSet = hostSetFactory.make();
            HostSet mapSet = hostSetFactory.make();
            for (AllocNode csObj : newPts) {
                AllocNode obj = ((ContextAllocNode)csObj).base();
                Type type = obj.getType();
                if(type instanceof RefType classType){
                    if(containerConfig.isHostType(classType)){
                        switch (ClassificationOf(classType)){
                            case MAP -> mapSet.addHost(containerConfig.getObjectHost(obj, Host.Classification.MAP));
                            case COLLECTION ->  colSet.addHost(containerConfig.getObjectHost(obj, Host.Classification.COLLECTION));
                        }
                    }
                }


                for (LocalVarNode virtualArray : arrayVarToVirtualArrayVar.get(var)) {
                    Node csVirtualArray = pta.parameterize(virtualArray, pta.emptyContext());
                    ValNode csArrayIdx = PTAUtils.getCsInstanceField(pta, csObj, ArrayElement.v());
                    pag.addEdge(csArrayIdx, csVirtualArray);
                }
            }

            if(!mapSet.isEmpty()){
                getHostMap(contextVarNode).addHostSet(HostList.Kind.MAP_0, mapSet);
                onNewHostEntry(contextVarNode, HostList.Kind.MAP_0, mapSet);
            }

            if(!colSet.isEmpty()){
                getHostMap(contextVarNode).addHostSet(COL_0, colSet);
                onNewHostEntry(contextVarNode, COL_0, colSet);
            }
        }
    }


    @Override
    public void onNewHostEntry(Node n, HostList.Kind kind, HostSet hostSet) {
        if(!(n instanceof ContextVarNode csVar) || !(csVar.base() instanceof LocalVarNode)) return;
        propagateHostAndKind(csVar, hostSet, kind);
        processMustRelatedInvokes(csVar, hostSet);
        LocalVarNode base = (LocalVarNode) csVar.base();
        SootMethod inMethod = base.getMethod();

        for (AssignStmt methodCallWithRetValue : ((CSCMethodNodeFactory)pag.getMethodPAG(inMethod).nodeFactory()).getInvokesWithReturn(base)) {
            InvokeExpr invoke = methodCallWithRetValue.getInvokeExpr();
            if (isRelatedEntranceInvoke(inMethod, invoke)) {
                for (SootMethod callee : cgb.getCalleesOfCallSite(invoke)) {
                    int nPara = callee.getParameterCount();
                    for (int i = 0; i < nPara; i++) {
                        if (!(callee.getParameterType(i) instanceof RefLikeType)) {
                            continue;
                        }
                        relateSourceToHosts(inMethod, invoke, callee, i, hostSet);
                    }
                }
            }
        }

        collectionVarToVirtualArrayVar.get(base).forEach(virtualArray -> {
            if (kind == COL_0)
                hostSet.forEach(host -> addSourceToHost(virtualArray, host, "Col-Value"));
            else {
                hostSet.forEach(host -> addSourceToHost(virtualArray, host, "Map-Value"));
                hostSet.forEach(host -> addSourceToHost(virtualArray, host, "Map-Key"));
            }
        });
        extenderLargerToSmaller.get(base).forEach(smaller -> {
            ContextVarNode smallerPointer = (ContextVarNode)pta.parameterize(smaller, pta.emptyContext()) ;
            HostList hostMap = getHostMap(smallerPointer);
            if (kind == COL_0) {
                if (hostMap.hasKind(COL_0)) {
                    addHostSubsetRelation(hostSet, Objects.requireNonNull(hostMap.getHostSetOf(COL_0)), true, false, false);
                }
                if (hostMap.hasKind(MAP_KEY_SET)) {
                    addHostSubsetRelation(hostSet, Objects.requireNonNull(hostMap.getHostSetOf(MAP_KEY_SET)), false, true, false);
                }
                if (hostMap.hasKind(MAP_VALUES)) {
                    addHostSubsetRelation(hostSet, Objects.requireNonNull(hostMap.getHostSetOf(MAP_VALUES)), false, false, true);
                }
            } else if (kind == MAP_0) {
                if (hostMap.hasKind(MAP_0)) {
                    addHostSubsetRelation(hostSet, Objects.requireNonNull(hostMap.getHostSetOf(MAP_0)),
                            true, false, false);
                }
            }
        });
        extenderSmallerToLarger.get(base).forEach(larger -> {
            ContextVarNode largerPointer = (ContextVarNode)pta.parameterize(larger, pta.emptyContext()) ;
            HostList hostMap = getHostMap(largerPointer);

            if (hostMap.hasKind(COL_0)) {
                HostSet set = hostMap.getHostSetOf(COL_0);
                if (kind == COL_0) {
                    addHostSubsetRelation(set, hostSet, true, false, false);
                }
                else if (kind == MAP_KEY_SET) {
                    addHostSubsetRelation(set, hostSet, false, true, false);
                }
                else if (kind == MAP_VALUES) {
                    addHostSubsetRelation(set, hostSet, false, false, true);
                }
            }
            if (kind == MAP_0) {
                if (hostMap.hasKind(MAP_0)) {
                    addHostSubsetRelation(Objects.requireNonNull(hostMap.getHostSetOf(MAP_0)), hostSet,
                            true, false, false);
                }
            }
        });
        hostPropagater.get(new Pair<>(kind, csVar)).forEach(kindCSVarPair -> {
            solver.addHostEntry(kindCSVarPair.second(), kindCSVarPair.first(), hostSet);
        });
        hostPropagater.get(new Pair<>(ALL, csVar)).forEach(kindCSVarPair -> {
            solver.addHostEntry(kindCSVarPair.second(), kind, hostSet);
        });
        for (HostList.Kind k: SetKindsInMap) {
            if (k == kind) {

                for (AssignStmt methodCallWithRetValue :((CSCMethodNodeFactory) pag.getMethodPAG(inMethod).nodeFactory()).getInvokesWithReturn(base)) {
                    for (SootMethod callee : cgb.getCalleesOfCallSite(methodCallWithRetValue.getInvokeExpr())) {
                        if(!callee.isStatic()){
                            LocalVarNode thisVar = (LocalVarNode) pag.getMethodPAG(callee).nodeFactory().caseThis();
                            Node csThis = pta.parameterize(thisVar, pta.emptyContext());
                            hostPropagater.put(new Pair<>(k, csVar), new Pair<>(k, (ContextVarNode) csThis));
                        }
                    }
                }
            }
        }
    }


    private void processArrayInitializer(SootMethod caller, InvokeExpr invoke, SootMethod callee) {
        solver.addSelectedMethod(callee);
        Pair<Integer, Integer> arrayCollectionPair = containerConfig.getArrayInitializer(callee);
        LocalVarNode arrayVar = getArgument(caller, invoke, arrayCollectionPair.first()),
                collectionVar = getArgument(caller, invoke, arrayCollectionPair.second());
        if (!(arrayVar.getType() instanceof ArrayType)) {
            throw new RuntimeException("Not Array Type!");
        }
        Type elementType = ((ArrayType) arrayVar.getType()).getElementType();

        LocalVarNode virtualArrayVar = new LocalVarNode("virtualArrayVar", elementType, caller);
        arrayVarToVirtualArrayVar.put(arrayVar, virtualArrayVar);
        collectionVarToVirtualArrayVar.put(collectionVar, virtualArrayVar);
        Node csArray = pta.parameterize(arrayVar, pta.emptyContext());
        Iterator<AllocNode> csArrayPtsItr = pta.reachingObjects(csArray).iterator();
        while(csArrayPtsItr.hasNext()){
            AllocNode csObj = csArrayPtsItr.next();
            ValNode arrayIdx = PTAUtils.getCsInstanceField(pta, csObj, ArrayElement.v());
            pag.addEdge(arrayIdx, pta.parameterize(virtualArrayVar, pta.emptyContext()));
        }
        ContextVarNode csCollection = (ContextVarNode) pta.parameterize(collectionVar, pta.emptyContext());
        HostList hostMap = getHostMap(csCollection);
        if (hostMap.hasKind(COL_0)) {
            hostMap.getHostSetOf(COL_0).forEach(host -> {
                addSourceToHost(virtualArrayVar, host, "Col-Value");
            });
        }
        else if (hostMap.hasKind(MAP_0)) {
            hostMap.getHostSetOf(MAP_0).forEach(host -> {
                addSourceToHost(virtualArrayVar, host, "Map-Key");
                addSourceToHost(virtualArrayVar, host, "Map-Value");
            });
        }
    }

    private boolean isRelatedEntranceInvoke(SootMethod container, InvokeExpr invokeExpr){
        return !containerConfig.isUnrelatedInInvoke(container, invokeExpr);
    }

    private void relateSourceToHosts(SootMethod caller, InvokeExpr invokeExpr, SootMethod callee, int index, HostSet hostset){
        RefType classType;
        for (String category : categories) {
            if((classType = containerConfig.getTypeConstraintOf(callee, index, category))!= null){
                solver.addSelectedMethod(callee);
                Value argV = invokeExpr.getArg(index);
                LocalVarNode argument = (LocalVarNode) pag.getMethodPAG(caller).nodeFactory().getNode(argV);
                RefType type = classType;
                hostset.hosts().filter(d -> !d.getTaint() && Scene.v().getOrMakeFastHierarchy().canStoreType(d.getType(), type))
                        .forEach(d -> addSourceToHost(argument, d, category));
            }
        }
    }
    private void addSourceToHost(LocalVarNode arg, Host host, String category){
        if(host.getTaint()) return;
        if(arg != null && isConcerned(arg.getType()) && host.addInArgument(arg, category)){
            Node csSource = pta.parameterize(arg, pta.emptyContext());
            HostPointer hostPointer = getHostPointer(host, category);
            solver.addIgnoreTypeFilterEdge(csSource, hostPointer);
            pag.addEdge(csSource, hostPointer);
        }
    }

    @Override
    public void onNewCallEdge(Edge edge) {
        Stmt stmt = edge.srcStmt();
        SootMethod caller = edge.src();
        SootMethod callee = edge.tgt();
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if (invokeExpr instanceof InstanceInvokeExpr instanceInvokeExpr) {
            Value baseV = instanceInvokeExpr.getBase();
            LocalVarNode baseVar = (LocalVarNode) pag.getMethodPAG(caller).nodeFactory().getNode(baseV);
            LocalVarNode calleeThis = (LocalVarNode) pag.getMethodPAG(callee).nodeFactory().caseThis();
            ContextVarNode csBase = (ContextVarNode) pta.parameterize(baseVar, pta.emptyContext());
            ContextVarNode csThis = (ContextVarNode) pta.parameterize(calleeThis, pta.emptyContext());
            HostList hostmap = getHostMap(csBase);
            for (HostList.Kind kind : SetKindsInMap) {
                if(hostmap.hasKind(kind)){
                    solver.addHostEntry(csThis, kind, hostmap.getHostSetOf(kind));
                    hostPropagater.put(Pair.of(kind, csBase), Pair.of(kind, csThis));
                }
            }
        }

        if (containerConfig.isCorrelationExtender(callee)) {
            processCorrelationExtender(caller, invokeExpr, callee);
        }
        if (containerConfig.getArrayInitializer(callee) != null) {
            processArrayInitializer(caller, invokeExpr, callee);
        }


        for (int i = 0; i < invokeExpr.getArgCount(); ++i) {
            Value arg = invokeExpr.getArg(i);
            if (isConcerned(arg.getType())) {
                if (isRelatedEntranceInvoke(caller, invokeExpr) && invokeExpr instanceof InstanceInvokeExpr instanceExp) {
                    Value base = instanceExp.getBase();
                    LocalVarNode baseVar = (LocalVarNode) pag.getMethodPAG(caller).nodeFactory().getNode(base);
                    ContextVarNode csBase = (ContextVarNode) pta.parameterize(baseVar, pta.emptyContext());
                    int finalI = i;
                    getHostMap(csBase).forEach((x, s) -> relateSourceToHosts(edge.src(), invokeExpr, callee, finalI, s));
                }
            }
        }
        processCollectionOutInvoke(caller, stmt, callee);
    }

    private static SootClass getOuterClass(SootClass inner) {
        if (inner != null) {
            if (inner.hasOuterClass())
                inner = inner.getOuterClass();
            return inner;
        }
        return null;
    }

    private static boolean isIteratorPollMethod(SootMethod method, ContainerConfig containerConfig) {
        String sig = method.getSubSignature();
        return containerConfig.isIteratorClass(method.getDeclaringClass()) && (sig.contains("next()") ||
                sig.contains("previous()"));
    }

    private static boolean isEnumerationPollMethod(SootMethod method) {
        return isEnumerationClass(method.getDeclaringClass())
                && method.getSubSignature().contains("nextElement()");
    }

    public static boolean cutReturnEdge(PAG pag, InvokeExpr callSite, SootMethod method){
        ContainerConfig config = ContainerConfig.config;
        String methodKind = config.CategoryOfExit(method);
        SootClass calleeClass = method.getDeclaringClass();
        String signature = method.getSubSignature();
        if (!Objects.equals(methodKind, "Other")) return true;

        if (callSite instanceof InstanceInvokeExpr e ) {
            Value baseV = e.getBase();
            CSCMethodNodeFactory calleeNodeFactory = (CSCMethodNodeFactory)pag.getMethodPAG(method).nodeFactory();
            LocalVarNode baseVar = (LocalVarNode) calleeNodeFactory.getNode(baseV);
            if(calleeNodeFactory.isRealThisVar(baseVar) || baseVar.equals(calleeNodeFactory.caseThis())) return false;

            if (!isIteratorClass(e.getMethod().getDeclaringClass()) &&
                    isMapEntryClass(calleeClass) && (signature.contains("getValue(") || signature.contains("getKey("))) {
                return true;
            }
            if (isEnumerationPollMethod(method)) {
                RefType outerType = getOuterClass(calleeClass).getType();
                return config.isHostType(outerType) || outerType.getSootClass().getName().equals("java.util.Collections");
            }
        }
        return false;
    }

    @Override
    public void onNewMethod(SootMethod method) {
//        if(method.isAbstract() || method.isPhantom() || !method.hasActiveBody()) return;
        MethodPAG methodPAG = pag.getMethodPAG(method);
        for (QueueReader<Node> reader = methodPAG.getInternalReader().clone(); reader.hasNext(); ) {
            Node from = reader.next();
            Node to = reader.next();
            if( from instanceof AllocNode obj){
                Type objType = obj.getType();
                SootClass clz = method.getDeclaringClass();
                if(objType instanceof RefType classType && isMapEntryClass(classType.getSootClass())){
                    containerConfig.getRelatedEntrySetClassesOf(clz).forEach(entry -> {
                        LocalVarNode arg = virtualARgsOfEntrySet.get(entry);
                        ValNode csArg = (ValNode)pta.parameterize(arg, pta.emptyContext());
                        ContextAllocNode csObj = (ContextAllocNode)pta.parameterize(obj, pta.emptyContext());
                        solver.propagatePTS(csObj, csArg, csObj, false);
                    });
                }

                if(objType instanceof RefType objRefType && containerConfig.isHostType(objRefType)){
                    Host newHost = null;
                    switch (ClassificationOf(objRefType)) {
                        case MAP ->  newHost = containerConfig.getObjectHost(obj, Host.Classification.MAP);
                        case COLLECTION -> newHost = containerConfig.getObjectHost(obj, Host.Classification.COLLECTION);
                    }
                    SootClass hostClass = objRefType.getSootClass();
                    if (containerConfig.isEntrySetClass(hostClass)) {
                        LocalVarNode arg = virtualARgsOfEntrySet.get(hostClass);
                        addSourceToHost(arg, newHost, "Col-Value");
                    }
                    if (containerConfig.isTaintAbstractListType(objType)) {
                        SootMethod get = abstractListToGet.get(hostClass);
                        if(get != null){
                            for (LocalVarNode ret :((CSCMethodNodeFactory)pag.getMethodPAG(get).nodeFactory()).getRealReturnVars()) {
                                addSourceToHost(ret, newHost, "Col-Value");
                            }
                        }
                    }
                }
                if(!method.isStatic()){
                    ContextVarNode csThis =(ContextVarNode) pta.parameterize(methodPAG.nodeFactory().caseThis(), pta.emptyContext());
                    ContextVarNode csLHS = (ContextVarNode)pta.parameterize(to, pta.emptyContext());
                    if(containerConfig.isKeySetClass(objType.toString())){
                        hostPropagater.put(new Pair<>(MAP_0, csThis), new Pair<>(MAP_KEY_SET, csLHS));
                    }
                    if(containerConfig.isValueSetClass(objType.toString())){
                        hostPropagater.put(new Pair<>(MAP_0, csThis), new Pair<>(MAP_VALUES, csLHS));
                    }
                }
            }
        }
    }

    private void processCollectionOutInvoke(SootMethod caller, Stmt callStmt, SootMethod callee) {
        if(!(callStmt instanceof AssignStmt callStmtWithRetVal)) return;
        Value lhsV = callStmtWithRetVal.getLeftOp();
        if(!PTAUtils.isConcerned(lhsV.getType())) return;
        LocalVarNode lhs = (LocalVarNode) pag.getMethodPAG(caller).nodeFactory().getNode(lhsV);
        String calleeSig = callee.getSignature();
        if (lhsV != null && isConcerned(lhsV.getType()) && callStmtWithRetVal.getInvokeExpr() instanceof InstanceInvokeExpr instanceExp) {
            String methodKind = containerConfig.CategoryOfExit(callee);
            Value baseV = instanceExp.getBase();
            LocalVarNode base = (LocalVarNode) pag.getMethodPAG(instanceExp.getMethod()).nodeFactory().getNode(baseV);

            ContextVarNode csBase = (ContextVarNode) pta.parameterize(base, pta.emptyContext());
            HostList hostMap = getHostMap(csBase);
            if (!Objects.equals(methodKind, "Other")) {
                solver.addSelectedMethod(callee);
                varToMustRelatedInvokes.put(base, Pair.of(callStmtWithRetVal, methodKind));

                if (Objects.equals(methodKind, "Col-Value")) {
                    if (hostMap.hasKind(COL_0)) {
                        hostMap.getHostSetOf(COL_0).forEach(host -> {
                            if(Scene.v().getOrMakeFastHierarchy().canStoreType(host.getType(), base.getType())){
                                checkHostRelatedExit(lhs, instanceExp, host, methodKind);
                            }
                        });
                    }
                }
                else {
                    if (hostMap.hasKind(MAP_0)) {
                        hostMap.getHostSetOf(MAP_0).forEach(host -> {
                            if(Scene.v().getOrMakeFastHierarchy().canStoreType(host.getType(), base.getType())){
                                checkHostRelatedExit(lhs, instanceExp, host, methodKind);
                            }
                        });
                    }
                }
            }
            if (isMapEntryClass(callee.getDeclaringClass()) && hostMap.hasKind(MAP_ENTRY)) {
                HostSet set = hostMap.getHostSetOf(MAP_ENTRY);
                if (set != null) {
                    if (calleeSig.contains("getValue(")) {
                        solver.addSelectedMethod(callee);
                        set.forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Map-Value"));
                    }
                    if (calleeSig.contains("getKey(")) {
                        solver.addSelectedMethod(callee);
                        set.forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Map-Key"));
                    }
                }
            }
            if (isIteratorClass(callee.getDeclaringClass()) && (calleeSig.contains("next()") ||
                    calleeSig.contains("previous()"))) {
                solver.addSelectedMethod(callee);
                if (hostMap.hasKind(MAP_VALUE_ITR))
                    hostMap.getHostSetOf(MAP_VALUE_ITR).forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Map-Value"));
                if (hostMap.hasKind(MAP_KEY_ITR))
                    hostMap.getHostSetOf(MAP_KEY_ITR).forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Map-Key"));
                if (hostMap.hasKind(COL_ITR))
                    hostMap.getHostSetOf(COL_ITR).forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Col-Value"));
            }
            if (isEnumerationClass(callee.getDeclaringClass()) && calleeSig.contains("nextElement()")) {
                solver.addSelectedMethod(callee);
                if (hostMap.hasKind(MAP_VALUE_ITR))
                    hostMap.getHostSetOf(MAP_VALUE_ITR).forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Map-Value"));
                if (hostMap.hasKind(MAP_KEY_ITR))
                    hostMap.getHostSetOf(MAP_KEY_ITR).forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Map-Key"));
                if (hostMap.hasKind(COL_ITR))
                    hostMap.getHostSetOf(COL_ITR).forEach(host -> checkHostRelatedExit(lhs, instanceExp, host, "Col-Value"));
            }
        }
    }


    private LocalVarNode getArgument(SootMethod caller, InvokeExpr instanceInvokeExpr, int index){
        Value v = index == -1 ? ((InstanceInvokeExpr)instanceInvokeExpr).getBase(): instanceInvokeExpr.getArg(index);
        return (LocalVarNode) pag.getMethodPAG(caller).nodeFactory().getNode(v);
    }


    private void processCorrelationExtender(SootMethod caller, InvokeExpr invokeExpr, SootMethod callee){
        solver.addSelectedMethod(callee);
        containerConfig.getCorrelationExtender(callee).forEach(indexPair -> {
            CSCMethodNodeFactory calleeNodeFactory = (CSCMethodNodeFactory)pag.getMethodPAG(callee).nodeFactory();
            if (invokeExpr instanceof InstanceInvokeExpr instanceExp) {
                Value baseV = instanceExp.getBase();
                LocalVarNode baseVar = (LocalVarNode) calleeNodeFactory.getNode(baseV);
                if(calleeNodeFactory.isRealThisVar(baseVar) || baseVar.equals(calleeNodeFactory.caseThis())) return;


                int largerIndex = indexPair.first(), smallerIndex = indexPair.second();
                LocalVarNode arg1 = getArgument(caller, instanceExp, largerIndex);
                LocalVarNode arg2 = getArgument(caller, instanceExp, smallerIndex);
                if (arg1 != null && arg2 != null && isConcerned(arg1.getType()) && isConcerned(arg2.getType())) {
                    extenderLargerToSmaller.put(arg1, arg2);
                    extenderSmallerToLarger.put(arg2, arg1);
                    ContextVarNode smallerPointer =(ContextVarNode) pta.parameterize(arg2, pta.emptyContext()), largerPointer = (ContextVarNode)pta.parameterize(arg1, pta.emptyContext());
                    HostList largerMap = getHostMap(largerPointer), smallerMap = getHostMap(smallerPointer);

                    if (largerMap.hasKind(COL_0)) {
                        HostSet set = largerMap.getHostSetOf(COL_0);
                        if (smallerMap.hasKind(COL_0)) {
                            addHostSubsetRelation(set, smallerMap.getHostSetOf(COL_0), true, false, false);
                        }
                        if (smallerMap.hasKind(MAP_KEY_SET)) {
                            addHostSubsetRelation(set, smallerMap.getHostSetOf(MAP_KEY_SET), false, true, false);
                        }
                        if (smallerMap.hasKind(MAP_VALUES)) {
                            addHostSubsetRelation(set, smallerMap.getHostSetOf(MAP_VALUES), false, false, true);
                        }
                    }
                    if (largerMap.hasKind(MAP_0) && smallerMap.hasKind(MAP_0)) {
                        addHostSubsetRelation(largerMap.getHostSetOf(MAP_0),
                                smallerMap.getHostSetOf(MAP_0), true, false, false);
                    }
                }
            }
        });
    }

    private void addHostSubsetRelation(HostSet largerHosts, HostSet smallerHosts, boolean newHost, boolean keySet, boolean values){
        smallerHosts.forEach(host -> {
            largerHosts.forEach(largerHost -> {
                boolean taint = containerConfig.isTaintType(host.getType());
                if(!largerHost.getTaint() && largerHost.getType() instanceof RefType classType && !classType.getClassName().contains("java.util.Collections$Empty")){
                    if (!taint && !host.getTaint()) {
                        if (newHost) {
                            if (host.getClassification() == Host.Classification.COLLECTION
                                    && largerHost.getClassification() == Host.Classification.COLLECTION) {
                                HostPointer smallerPointer = getHostPointer(host,"Col-Value"),
                                        largerPointer = getHostPointer(largerHost, "Col-Value");
                                solver.addIgnoreTypeFilterEdge(smallerPointer, largerPointer);
                                pag.addEdge(smallerPointer, largerPointer);
                            }
                            if (host.getClassification() == Host.Classification.MAP
                                    && largerHost.getClassification() == Host.Classification.MAP) {
                                HostPointer smallerPointer = getHostPointer(host, "Map-Key"),
                                        largerPointer = getHostPointer(largerHost, "Map-Key");
                                solver.addIgnoreTypeFilterEdge(smallerPointer, largerPointer);
                                pag.addEdge(smallerPointer, largerPointer);
                                smallerPointer = getHostPointer(host, "Map-Value");
                                largerPointer = getHostPointer(largerHost, "Map-Value");
                                solver.addIgnoreTypeFilterEdge(smallerPointer, largerPointer);
                                pag.addEdge(smallerPointer, largerPointer);

                            }
                        }
                        if (keySet && host.getClassification() == Host.Classification.MAP
                                && largerHost.getClassification() == Host.Classification.COLLECTION) {
                            HostPointer smallerPointer = getHostPointer(host, "Map-Key"),
                                    largerPointer = getHostPointer(largerHost, "Col-Value");
                            solver.addIgnoreTypeFilterEdge(smallerPointer, largerPointer);
                            pag.addEdge(smallerPointer, largerPointer);
                        }
                        if (values && host.getClassification() == Host.Classification.MAP
                                && largerHost.getClassification() == Host.Classification.COLLECTION) {
                            HostPointer smallerPointer = getHostPointer(host, "Map-Value"),
                                    largerPointer = getHostPointer(largerHost, "Col-Value");
                            solver.addIgnoreTypeFilterEdge(smallerPointer, largerPointer);
                            pag.addEdge(smallerPointer, largerPointer);
                        }
                    }
                    else {
                        taintHost(largerHost);
                    }
                }
            });
        });
    }

    private void propagateHostAndKind(ContextVarNode csVar, HostSet hostset, HostList.Kind kind){
        LocalVarNode varBase = (LocalVarNode) csVar.base();

        CSCMethodNodeFactory nodeFactory = (CSCMethodNodeFactory)pag.getMethodPAG(varBase.getMethod()).nodeFactory();
        for (AssignStmt methodCallWithRetValue : nodeFactory.getInvokesWithReturn(varBase)) {
            Value v = methodCallWithRetValue.getLeftOp();
            if(!PTAUtils.isConcerned(v.getType())) continue;
            LocalVarNode lhs = (LocalVarNode) nodeFactory.getNode(v);
            String invokeString = methodCallWithRetValue.getInvokeExpr().getMethodRef().getName();
            Node csLhs = pta.parameterize(lhs, pta.emptyContext());
            ContainerConfig.getHostGenerators().forEach((kind_ori, keyString, kind_gen) ->{
                if(kind == kind_ori && invokeString.contains(keyString)){
                    solver.addHostEntry(csLhs, kind_gen, hostset);
                }
            });
            InvokeExpr invoke = methodCallWithRetValue.getInvokeExpr();
            ContainerConfig.getNonContainerExits().forEach((kind_required, invoke_str, category) -> {
                if(kind == kind_required && invokeString.contains(invoke_str)){
                    hostset.forEach(host -> checkHostRelatedExit(lhs, invoke, host, category));
                }
            });

            switch (kind) {
                case COL_0 -> {
                    if(invokeString.equals("elements") &&  isVectorType(varBase.getType())){
                        solver.addHostEntry(csLhs, HostList.Kind.MAP_VALUE_ITR, hostset);
                    }
                }
                case MAP_0 -> {
                    if ((invokeString.equals("elements") && isHashtableType(varBase.getType()))) {
                        solver.addHostEntry(csLhs, MAP_VALUE_ITR, hostset);
                    }
                }
            }
        }
    }

    private void processMustRelatedInvokes(ContextVarNode csVar, HostSet hostSet){
        if(csVar.base() instanceof LocalVarNode var){
            for (Pair<AssignStmt, String> invokeExprStringPair : varToMustRelatedInvokes.get(var)) {
                AssignStmt callStmtWithReturnVal = invokeExprStringPair.first();
                if(!callStmtWithReturnVal.containsInvokeExpr()) throw new RuntimeException("Not an invoke statement");
                Value lhsV = callStmtWithReturnVal.getLeftOp();
                LocalVarNode lhs = (LocalVarNode) pag.getMethodPAG(var.getMethod()).nodeFactory().getNode(lhsV);
                String invokeString = invokeExprStringPair.second();
                if(lhs != null && isConcerned(lhs.getType())){
                    for (Host host : hostSet) {
                        if(Scene.v().getOrMakeFastHierarchy().canStoreType(host.getType(), lhs.getType())){
                            checkHostRelatedExit(lhs, callStmtWithReturnVal.getInvokeExpr(), host, invokeString);
                        }
                    }
                }
            }
        }
    }

    private void recoverCallSite(LocalVarNode lhs, InvokeExpr callSite){
        if(solver.addRecoveredCallSite(callSite)){
            for (SootMethod callee : cgb.getCalleesOfCallSite(callSite)) {
                MethodNodeFactory nodeFactory = pag.getMethodPAG(callee).nodeFactory();
                LocalVarNode qilinRetVar = (LocalVarNode) nodeFactory.caseRet();
                Node csQilinRetVar = pta.parameterize(qilinRetVar, pta.emptyContext());
                Node csLhs = pta.parameterize(lhs, pta.emptyContext());
                solver.addReturnEdge(csQilinRetVar, csLhs);
                solver.addIgnoreTypeFilterEdge(csQilinRetVar, csLhs);
                pag.addEdge(csQilinRetVar, csLhs);
            }
        }
    }


    private void taintHost(Host host) {
        if (!host.getTaint() && !containerConfig.isTaintAbstractListType(host.getType())) {
            host.setTaint();
            hostToExits.get(host).forEach(p -> recoverCallSite(p.first(), p.second()));
            for (String cat: categories) {
                pag.succNodeOfHost(getHostPointer(host,cat)).forEach(succ -> {
                    if (succ instanceof HostPointer hostPointer) {
                        taintHost(hostPointer.getHost());
                    }
                });
            }
        }
    }

    private void checkHostRelatedExit(LocalVarNode lhs, InvokeExpr callSite, Host host, String category){
        if(lhs != null && isConcerned(lhs.getType()) && !solver.isRecoveredCallSite(callSite)){
            if(host.getTaint()){
                recoverCallSite(lhs, callSite);
            }else{
                hostToExits.put(host, Pair.of(lhs, callSite));
                addTargetToHost(lhs, host, category);
            }
        }
    }



    private void addTargetToHost(LocalVarNode result, Host host, String category){
        if(result != null && isConcerned(result.getType()) && host.addOutResult(result, category)){
            HostPointer hostPointer = getHostPointer(host, category);
            Node csTarget = pta.parameterize(result, pta.emptyContext());
            solver.addIgnoreTypeFilterEdge(hostPointer, csTarget);
            pag.addEdge(hostPointer, csTarget);
        }
    }





    @Override
    public void onNewPFGEdge(Node src, Node tgt) {
        if(src instanceof ContextVarNode varSrc && tgt instanceof ContextVarNode csTgt){
            if(solver.needPropagateHost(varSrc, tgt)){
                HostList hostMap = getHostMap(varSrc);
                if(!hostMap.isEmpty()){
                    hostMap.forEach((k,set) -> solver.addHostEntry(csTgt, k, set));
                }
            }
        }

    }

    private final TwoKeyMap<Host, String, HostPointer> hostPointers = Maps.newTwoKeyMap();

    private int counter = 0;
    private HostPointer getHostPointer(Host host, String category){
        return hostPointers.computeIfAbsent(host, category, (h, c) -> new HostPointer(h, c, counter ++));
    }

    private HostList getHostMap(ContextVarNode csVar){
        return csVarToHostMap.computeIfAbsent(csVar, __ -> new HostList());
    }
}
