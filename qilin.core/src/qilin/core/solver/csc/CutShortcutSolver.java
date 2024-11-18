package qilin.core.solver.csc;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import pascal.taie.collection.Sets;
import pascal.taie.collection.TwoKeyMultiMap;
import qilin.CoreConfig;
import qilin.core.PTA;
import qilin.core.PTAScene;
import qilin.core.builder.CSCCallGraphBuilder;
import qilin.core.builder.ExceptionHandler;
import qilin.core.builder.MethodNodeFactory;
import qilin.core.pag.*;
        import qilin.core.sets.DoublePointsToSet;
import qilin.core.sets.P2SetVisitor;
import qilin.core.sets.PointsToSetInternal;
import qilin.core.solver.Propagator;
import qilin.core.solver.csc.plugin.CompositePlugin;
import qilin.core.solver.csc.plugin.Plugin;
import qilin.core.solver.csc.plugin.container.ContainerAccessHandler;
import qilin.core.solver.csc.plugin.container.ContainerConfig;
import qilin.core.solver.csc.plugin.container.HostMap.HostList;
import qilin.core.solver.csc.plugin.container.HostMap.HostSet;
import qilin.core.solver.csc.plugin.container.element.HostPointer;
import qilin.core.solver.csc.plugin.field.FieldAccessHandler;
import qilin.core.solver.csc.plugin.field.ParameterIndex;
import qilin.core.solver.csc.plugin.localflow.LocalFlowHandler;
import qilin.util.PTAUtils;
import soot.*;
        import soot.jimple.*;
        import soot.jimple.spark.pag.SparkField;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.util.NumberedString;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;

import java.util.*;

import static qilin.core.solver.csc.plugin.container.ClassAndTypeClassifier.isHashtableClass;
import static qilin.core.solver.csc.plugin.container.ClassAndTypeClassifier.isVectorClass;


public class CutShortcutSolver extends Propagator {
    private final TreeSet<ValNode> valNodeWorkList = new TreeSet<>();
    private final Queue<CSCEntry> cscWorkList = new ArrayDeque<>();
    private final CSCPAG pag;
    private final PTA pta;
    private final CSCCallGraphBuilder cgb;
    private final ExceptionHandler eh;
    private final ChunkedQueue<ExceptionThrowSite> throwSiteQueue = new ChunkedQueue<>();
    private final ChunkedQueue<VirtualCallSite> virtualCallSiteQueue = new ChunkedQueue<>();
    private final ChunkedQueue<Node> edgeQueue = new ChunkedQueue<>();

    private final ChunkedQueue<MethodOrMethodContext> newRMQueue = new ChunkedQueue<>();
    private final Set<LocalVarNode> qilinReturnVarsSpeciallyHandled = Sets.newSet();
    private final TwoKeyMultiMap<LocalVarNode, Field, LocalVarNode> ignoredStoreFields = Maps.newTwoKeyMultiMap();
    private final MultiMap<FieldRefNode, LocalVarNode> fieldLoadOperationWithDisableRelay = Maps.newMultiMap();

    private final MultiMap<Node, Node> returnEdges = Maps.newMultiMap();
    private final MultiMap<Node, Node> disableTypeFilteringEdges = Maps.newMultiMap();
    private final Map<ContextVarNode, HostList> csVarTohostMap = Maps.newMap();
    private final Set<InvokeExpr> recoveredCallSites = Sets.newSet();

    private final Plugin plugin;

    public Map<ContextVarNode, HostList> getCsVarTohostMap() {
        return csVarTohostMap;
    }
    public CSCPAG getPag() {
        return pag;
    }

    public CSCCallGraphBuilder getCgb() {
        return cgb;
    }

    public PTA getPta() {
        return pta;
    }

    public CutShortcutSolver(PTA pta) {
        this.cgb = (CSCCallGraphBuilder) pta.getCgb();

        this.cgb.setRMQueue(newRMQueue);
        cgb.setReturnEdges(returnEdges);
        cgb.setRecoveredCallSites(recoveredCallSites);
        cgb.setQilinReturnVarsSpeciallyHandled(qilinReturnVarsSpeciallyHandled);
        this.pag = (CSCPAG) pta.getPag();
        this.pag.setEdgeQueue(edgeQueue);
        this.eh = pta.getExceptionHandler();
        this.pta = pta;

        // add plugins
        CompositePlugin plugin = new CompositePlugin();
        this.plugin = plugin;
        plugin.addPlugin(new LocalFlowHandler());
        plugin.addPlugin(new FieldAccessHandler());
        plugin.addPlugin(new ContainerAccessHandler(cgb.getInvokeToLine()));
        this.cgb.setPlugin(plugin);
        plugin.setSolver(this);
        plugin.onStart();
    }

    @Override
    public void propagate() {
        final QueueReader<MethodOrMethodContext> newRMs = newRMQueue.reader();
        final QueueReader<Node> newPAGEdges = edgeQueue.reader();
        final QueueReader<ExceptionThrowSite> newThrows = throwSiteQueue.reader();
        final QueueReader<VirtualCallSite> newCalls = virtualCallSiteQueue.reader();

        cgb.initReachableMethods();
        processStmts(newRMs);
        pag.getAlloc().forEach((a, set) -> set.forEach(v -> propagatePTS(a, v, a, false)));
        while (!valNodeWorkList.isEmpty() || !cscWorkList.isEmpty()) {
            if(!cscWorkList.isEmpty()){
                CSCEntry entry = cscWorkList.poll();
                if (entry instanceof GetStmtEntry getStmtEntry) {
                    plugin.onNewGetStatement(getStmtEntry.method, getStmtEntry.baseIndex, getStmtEntry.field);
                } else if (entry instanceof SetStmtEntry setStmtEntry) {
                    plugin.onNewSetStatement(setStmtEntry.method, setStmtEntry.field, setStmtEntry.baseIndex, setStmtEntry.rhsIndex);
                } else if(entry instanceof HostEntry hostEntry){
                    Node p = hostEntry.csLhs();
                    if(p instanceof ContextVarNode contextVarNode && contextVarNode.base() instanceof LocalVarNode){
                        HostSet diff = processHostEntry(hostEntry);
                        if(!diff.isEmpty())
                            plugin.onNewHostEntry(hostEntry.csLhs, hostEntry.kind, hostEntry.hostset);
                    }
                }
            }
            if(!valNodeWorkList.isEmpty()){
              ValNode curr = valNodeWorkList.pollFirst();
              // Step 1: Resolving Direct Constraints
              assert curr != null;
              final DoublePointsToSet pts = curr.getP2Set();
              final PointsToSetInternal newset = pts.getNewSet();
              Set<ValNode> set = new HashSet<>(pag.simpleLookup(curr));
              set.forEach(to -> {
                  propagatePTS(curr, to, newset, curr instanceof HostPointer || to instanceof HostPointer);
              });
              if (curr instanceof VarNode currVarNode) {

                  // Step 1 continues.
                  Collection<ExceptionThrowSite> throwSites = eh.throwSitesLookUp(currVarNode);
                  for (ExceptionThrowSite site : throwSites) {
                      eh.exceptionDispatch(newset, site);
                  }
                  // Step 2: Resolving Indirect Constraints.
                  handleStoreAndLoadOnBase(currVarNode);
                  // Step 3: Collecting New Constraints.
                  Collection<VirtualCallSite> sites = cgb.callSitesLookUp(currVarNode);
                  for (VirtualCallSite site : sites) {
                      cgb.virtualCallDispatch(newset, site);
                  }
                  processStmts(newRMs);
              }
              pts.flushNew();
              // Step 4: Activating New Constraints.
              activateConstraints(newCalls, newRMs, newThrows, newPAGEdges);
            }
        }
    }

    private HostSet processHostEntry(HostEntry hostEntry){
        ContextVarNode pointer = (ContextVarNode) hostEntry.csLhs();
        HostSet hostSet = hostEntry.hostset;
        HostList.Kind kind = hostEntry.kind;
        HostSet diff = csVarTohostMap.computeIfAbsent(pointer, k -> new HostList()).addAllDiff(kind, hostSet);
        if(!diff.isEmpty()){
            if( pointer.base() instanceof LocalVarNode){
                for (Node succ : pag.succNodesOfCSVar(pointer)) {
                    if (needPropagateHost(pointer, succ)) {
                        addHostEntry(succ, kind, diff);
                    }
                }
            }
        }
        return diff;
    }

    private static final String[] stopSigns = new String[]{"iterator(", "entrySet()", "keySet()", "values()", "Entry(", "Iterator("};

    public boolean needPropagateHost(ContextVarNode source, Node target){
        if(returnEdges.get(source).contains(target)){
            LocalVarNode sourceVar = (LocalVarNode)source.base();
            SootClass container = sourceVar.getMethod().getDeclaringClass();
            String methodString = sourceVar.getMethod().toString();
            if(ContainerConfig.config.isRealHostClass(container)){
                for (String stopSign : stopSigns) {
                    if(methodString.contains(stopSign)){
                        return false;
                    }
                }
                if(isHashtableClass(container) && (methodString.contains("elements()") || methodString.contains("keys()"))) {
                    return false;
                }
                return !isVectorClass(container) || !methodString.contains("elements()");
            }
            return true;
        }
        return true;
    }

    public boolean addReturnEdge(Node source, Node target){
        return returnEdges.put(source, target);
    }

    public void addIgnoreTypeFilterEdge(Node source, Node target){
        this.disableTypeFilteringEdges.put(source, target);
    }

    public boolean isIgnoreTypeFieldEdge(Node source, Node target){
        return disableTypeFilteringEdges.get(source).contains(target);
    }

    public void processStmts(Iterator<MethodOrMethodContext> newRMs) {
        while (newRMs.hasNext()) {
            MethodOrMethodContext momc = newRMs.next();
            SootMethod method = momc.method();
            if (method.isPhantom()) {
                continue;
            }



            MethodPAG mpag = pag.getMethodPAG(method);
            addToPAG(mpag, momc.context());
            // !FIXME in a context-sensitive pointer analysis, clinits in a method maybe added multiple times.
            if (CoreConfig.v().getPtaConfig().clinitMode == CoreConfig.ClinitMode.ONFLY) {
                // add <clinit> find in the method to reachableMethods.
                Iterator<SootMethod> it = mpag.triggeredClinits();
                while (it.hasNext()) {
                    SootMethod sm = it.next();
                    cgb.injectCallEdge(sm.getDeclaringClass().getType(), pta.parameterize(sm, pta.emptyContext()), Kind.CLINIT);
                }
            }
            recordCallStmts(momc, mpag.getInvokeStmts());
            recordThrowStmts(momc, mpag.stmt2wrapperedTraps.keySet());
        }
    }

    private void recordCallStmts(MethodOrMethodContext m, Collection<Unit> units) {
        for (final Unit u : units) {
            final Stmt s = (Stmt) u;
            if (s.containsInvokeExpr()) {
                cgb.addInvokeToLine(s);
                InvokeExpr ie = s.getInvokeExpr();
                if (ie instanceof InstanceInvokeExpr iie) {
                    Local receiver = (Local) iie.getBase();
                    VarNode recNode = cgb.getCSReceiverVarNode(receiver, m);
                    NumberedString subSig = iie.getMethodRef().getSubSignature();
                    VirtualCallSite virtualCallSite = new VirtualCallSite(recNode, s, m, iie, subSig, Edge.ieToKind(iie));
                    if (cgb.recordVirtualCallSite(recNode, virtualCallSite)) {
                        virtualCallSiteQueue.add(virtualCallSite);
                    }
                } else {
                    SootMethod tgt = ie.getMethod();
                    if (tgt != null) { // static invoke or dynamic invoke
                        VarNode recNode = pag.getMethodPAG(m.method()).nodeFactory().caseThis();
                        recNode = (VarNode) pta.parameterize(recNode, m.context());
                        if (ie instanceof DynamicInvokeExpr) {
                            // !TODO dynamicInvoke is provided in JDK after Java 7.
                            // currently, PTA does not handle dynamicInvokeExpr.
                        } else {
                            cgb.addStaticEdge(m, s, tgt, Edge.ieToKind(ie));
                        }
                    } else if (!Options.v().ignore_resolution_errors()) {
                        throw new InternalError("Unresolved target " + ie.getMethod()
                                + ". Resolution error should have occured earlier.");
                    }
                }
            }
        }
    }

    private void recordThrowStmts(MethodOrMethodContext m, Collection<Stmt> stmts) {
        for (final Stmt stmt : stmts) {
            SootMethod sm = m.method();
            MethodPAG mpag = pag.getMethodPAG(sm);
            MethodNodeFactory nodeFactory = mpag.nodeFactory();
            Node src;
            if (stmt.containsInvokeExpr()) {
                src = nodeFactory.makeInvokeStmtThrowVarNode(stmt, sm);
            } else {
                assert stmt instanceof ThrowStmt;
                ThrowStmt ts = (ThrowStmt) stmt;
                src = nodeFactory.getNode(ts.getOp());
            }
            VarNode throwNode = (VarNode) pta.parameterize(src, m.context());
            ExceptionThrowSite throwSite = new ExceptionThrowSite(throwNode, stmt, m);
            if (eh.addThrowSite(throwNode, throwSite)) {
                throwSiteQueue.add(throwSite);
            }
        }
    }

    private void addToPAG(MethodPAG mpag, Context cxt) {
        Set<Context> contexts = pag.getMethod2ContextsMap().computeIfAbsent(mpag, k1 -> new HashSet<>());
        if (!contexts.add(cxt)) {
            return;
        }
        for (QueueReader<Node> reader = mpag.getInternalReader().clone(); reader.hasNext(); ) {
            Node from = reader.next();
            Node to = reader.next();
            if (from instanceof AllocNode heap) {
                from = pta.heapAbstractor().abstractHeap(heap);
            }
            if (from instanceof AllocNode && to instanceof GlobalVarNode) {
                pag.addGlobalPAGEdge(from, to);
            } else {
                from = pta.parameterize(from, cxt);
                to = pta.parameterize(to, cxt);
                if (from instanceof AllocNode) {
                    handleImplicitCallToFinalizerRegister((AllocNode) from);
                }
                pag.addEdge(from, to);
            }
        }
    }

    // handle implicit calls to java.lang.ref.Finalizer.register by the JVM.
    // please refer to library/finalization.logic in doop.
    private void handleImplicitCallToFinalizerRegister(AllocNode heap) {
        if (PTAUtils.supportFinalize(heap)) {
            SootMethod rm = PTAScene.v().getMethod("<java.lang.ref.Finalizer: void register(java.lang.Object)>");
            MethodPAG tgtmpag = pag.getMethodPAG(rm);
            MethodNodeFactory tgtnf = tgtmpag.nodeFactory();
            Node parm = tgtnf.caseParm(0);
            Context calleeCtx = pta.emptyContext();
            AllocNode baseHeap = heap.base();
            parm = pta.parameterize(parm, calleeCtx);
            pag.addEdge(heap, parm);
            cgb.injectCallEdge(baseHeap, pta.parameterize(rm, calleeCtx), Kind.STATIC);
        }
    }

    private void handleStoreAndLoadOnBase(VarNode base) {
        for (final FieldRefNode fr : base.getAllFieldRefs()) {

            for (final VarNode v : pag.storeInvLookup(fr)) {
                if(fr.getField() instanceof Field field){
                    if(base.base() instanceof LocalVarNode baseVar && v.base() instanceof LocalVarNode rhs){
                        if(ignoredStoreFields.get(baseVar, field).contains(rhs)){
                            continue;
                        }
                    }
                }

                handleStoreEdge(base.getP2Set().getNewSet(), fr.getField(), v);

            }
            for (final VarNode to : pag.loadLookup(fr)) {
                handleLoadEdge(base.getP2Set().getNewSet(), fr.getField(), to, fr);
            }
        }
    }

    private void handleStoreEdge(PointsToSetInternal baseHeaps, SparkField field, ValNode from) {
        baseHeaps.forall(new P2SetVisitor(pta) {
            public void visit(Node n) {
                if (disallowStoreOrLoadOn((AllocNode) n)) {
                    return;
                }
                final FieldValNode fvn = pag.makeFieldValNode(field);
                final ValNode oDotF = (ValNode) pta.parameterize(fvn, PTAUtils.plusplusOp((AllocNode) n));
                pag.addEdge(from, oDotF);
            }
        });
    }

    private void handleLoadEdge(PointsToSetInternal baseHeaps, SparkField field, ValNode to, FieldRefNode fr) {
        baseHeaps.forall(new P2SetVisitor(pta) {
            public void visit(Node n) {
                if (disallowStoreOrLoadOn((AllocNode) n)) {
                    return;
                }
                final FieldValNode fvn = pag.makeFieldValNode(field);
                final ValNode oDotF = (ValNode) pta.parameterize(fvn, PTAUtils.plusplusOp((AllocNode) n));
                if(to instanceof ContextVarNode csTo && csTo.base() instanceof LocalVarNode baseVar ){
                    if(fieldLoadOperationWithDisableRelay.get(fr).contains(baseVar)){
                        plugin.onNewNonRelayLoadEdge(oDotF, to);
                    }
                }else if(to instanceof LocalVarNode){
                    throw new RuntimeException("should not be a ci var in handle load edge, but a cs var.");
                }

                pag.addEdge(oDotF, to);
            }
        });
    }

    public void putFieldLoadWithDisableRelay(FieldRefNode fr, LocalVarNode to){
        fieldLoadOperationWithDisableRelay.put(fr, to);
    }

    private void activateConstraints(QueueReader<VirtualCallSite> newCalls, QueueReader<MethodOrMethodContext> newRMs, QueueReader<ExceptionThrowSite> newThrows, QueueReader<Node> addedEdges) {
        while (newCalls.hasNext()) {
            while (newCalls.hasNext()) {
                final VirtualCallSite site = newCalls.next();
                final VarNode receiver = site.recNode();
                cgb.virtualCallDispatch(receiver.getP2Set().getOldSet(), site);
            }
            processStmts(newRMs); // may produce new calls, thus an out-loop is a must.
        }

        while (newThrows.hasNext()) {
            final ExceptionThrowSite ets = newThrows.next();
            final VarNode throwNode = ets.getThrowNode();
            eh.exceptionDispatch(throwNode.getP2Set().getOldSet(), ets);
        }
        /*
         * there are some actual parameter to formal parameter edges whose source nodes are not in the worklist.
         * For this case, we should use the following loop to update the target nodes and insert the
         * target nodes into the worklist if nesseary.
         * */
        while (addedEdges.hasNext()) {
            Node src = addedEdges.next();
            Node tgt = addedEdges.next();
            addPTSOnNewPagEdge(src, tgt);
        }
    }


    public void addPTSOnNewPagEdge(Node addedSrc, Node addedTgt){
        if (addedSrc instanceof VarNode && addedTgt instanceof VarNode
                || addedSrc instanceof ContextField || addedTgt instanceof ContextField || addedSrc instanceof HostPointer || addedTgt instanceof HostPointer
        ) { // x = y; x = o.f; o.f = y;
            final ValNode srcv = (ValNode) addedSrc;
            final ValNode tgtv = (ValNode) addedTgt;
            propagatePTS(srcv, tgtv, srcv.getP2Set().getOldSet(), srcv instanceof HostPointer || tgtv instanceof HostPointer);
        } else if (addedSrc instanceof final FieldRefNode srcfrn) { // b = a.f
            handleLoadEdge(srcfrn.getBase().getP2Set().getOldSet(), srcfrn.getField(), (ValNode) addedTgt, srcfrn);
        } else if (addedTgt instanceof final FieldRefNode tgtfrn) { // a.f = b;
            handleStoreEdge(tgtfrn.getBase().getP2Set().getOldSet(), tgtfrn.getField(), (ValNode) addedSrc);
        } else if (addedSrc instanceof AllocNode) { // alloc x = new T;
            propagatePTS(addedSrc, (VarNode) addedTgt, (AllocNode) addedSrc, false);
        }
    }

    public void propagatePTS(Node from, final ValNode pointer, PointsToSetInternal other, boolean disableTypeFiltering) {
        disableTypeFiltering |= this.disableTypeFilteringEdges.get(from).contains(pointer);
        final DoublePointsToSet addTo = pointer.getP2Set();
        boolean finalDisableTypeFiltering = disableTypeFiltering;
        P2SetVisitor p2SetVisitor = new P2SetVisitor(pta) {
            @Override
            public void visit(Node n) {
                if(!finalDisableTypeFiltering){
                    if (PTAUtils.addWithTypeFiltering(addTo, pointer.getType(), n)) {
                        this.newObjs.add((AllocNode) n);
                        returnValue = true;
                    }
                }else{
                    if (PTAUtils.addWithoutTypeFiltering(addTo, n)) {
                        this.newObjs.add((AllocNode) n);
                        returnValue = true;
                    }
                }

            }
        };
        other.forall(p2SetVisitor);
        if (p2SetVisitor.getReturnValue()) {
            boolean added = valNodeWorkList.add(pointer);
            plugin.onNewPointsToSet(pointer, p2SetVisitor.newObjs);
        }
    }

    public void propagatePTS(Node from, final ValNode pointer, AllocNode heap, boolean disableTypeFiltering) {
        disableTypeFiltering |= this.disableTypeFilteringEdges.get(from).contains(pointer);
        if(!disableTypeFiltering){
            if (PTAUtils.addWithTypeFiltering(pointer.getP2Set(), pointer.getType(), heap)) {
                plugin.onNewPointsToSet(pointer, Set.of(heap));
                valNodeWorkList.add(pointer);
            }
        }else{
            if(PTAUtils.addWithoutTypeFiltering(pointer.getP2Set(), heap)){
                plugin.onNewPointsToSet(pointer, Set.of(heap));
                valNodeWorkList.add(pointer);
            }
        }

    }

    // we do not allow store to and load from constant heap/empty array.
    private boolean disallowStoreOrLoadOn(AllocNode heap) {
        AllocNode base = heap.base();
        // return base instanceof StringConstantNode || PTAUtils.isEmptyArray(base);
        return PTAUtils.isEmptyArray(base);
    }


    public boolean addRecoveredCallSite(InvokeExpr expr){
        return recoveredCallSites.add(expr);
    }
    public boolean isRecoveredCallSite(InvokeExpr expr){
        return recoveredCallSites.contains(expr);
    }

    public void addSelectedMethod(SootMethod method){
    }

    public void addSpecialHandledRetVar(LocalVarNode retVar){
        if(!retVar.isReturn()) throw new RuntimeException("Not qilin return var.");
        qilinReturnVarsSpeciallyHandled.add(retVar);
    }

    public void addIgnoredStoreField(LocalVarNode base, Field field, LocalVarNode rhs){
        this.ignoredStoreFields.put(base, field, rhs);
    }


    public void addGetStmtEntry(SootMethod method, ParameterIndex baseIndex, Field field){
        cscWorkList.add(new GetStmtEntry(method, baseIndex, field));
    }
    public void addSetStmtEntry(SootMethod method, Field field, ParameterIndex baseIndex, ParameterIndex rhsIndex){
        cscWorkList.add(new SetStmtEntry(method, field, baseIndex, rhsIndex));
    }

    public void addHostEntry(Node csLhs, HostList.Kind kind, HostSet hostset){
        cscWorkList.add(new HostEntry(csLhs, kind, hostset));
    }


    interface CSCEntry {}
    public record SetStmtEntry(SootMethod method, Field field, ParameterIndex baseIndex, ParameterIndex rhsIndex) implements CSCEntry{}
    public record GetStmtEntry(SootMethod method, ParameterIndex baseIndex, Field field) implements CSCEntry{}
    public record HostEntry(Node csLhs, HostList.Kind kind, HostSet hostset) implements CSCEntry{}
    public void onNewPFGEdge(Node src, Node tgt){
        plugin.onNewPFGEdge(src, tgt);
    }
}
