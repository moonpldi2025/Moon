package qilin.core.solver.csc.plugin.field;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import pascal.taie.collection.Sets;
import qilin.core.PTA;
import qilin.core.builder.CSCCallGraphBuilder;
import qilin.core.builder.CallGraphBuilder;
import qilin.core.builder.CSCMethodNodeFactory;
import qilin.core.pag.*;
import qilin.core.solver.csc.CutShortcutSolver;
import qilin.core.solver.csc.plugin.Plugin;
import qilin.core.solver.csc.plugin.container.ContainerAccessHandler;
import qilin.util.PTAUtils;
import soot.RefLikeType;
import soot.Scene;
import soot.SootMethod;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.queue.QueueReader;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class FieldAccessHandler implements Plugin {
    private final MultiMap<SootMethod, SetStatement> setStatements = Maps.newMultiMap();
    private final MultiMap<SootMethod, GetStatement> getStatements = Maps.newMultiMap();

    private final Set<LocalVarNode> bannedRealReturnVars = Sets.newSet();
    private final MultiMap<Node, Node> nonRelayEdgeSrcToTgtWithOnlyRealParam = Maps.newMultiMap();
    private final Map<LocalVarNode, ParameterIndex> varToIndexWithOnlyRealParam = Maps.newMap();
    private final Set<LocalVarNode> leftDefinedVarsWithoutRealParam = Sets.newSet();
    private final MultiMap<LocalVarNode, AbstractLoadField> varToAbstractLoadFields = Maps.newMultiMap();
    private final MultiMap<LocalVarNode, AbstractStoreField> varToAbstractStoreFields = Maps.newMultiMap();
    private CutShortcutSolver solver;
    private PTA pta;
    private CSCPAG pag;
    private CSCCallGraphBuilder cgb;
    @Override
    public void setSolver(CutShortcutSolver solver) {
        this.solver = solver;
        this.pta = solver.getPta();
        this.pag = solver.getPag();
        this.cgb = solver.getCgb();
    }

    public boolean addSetStatement(SootMethod container, ParameterIndex baseIndex, Field field, ParameterIndex rhsIndex){
        SetStatement setStatement = new SetStatement(baseIndex, field, rhsIndex);
        if(setStatements.get(container).contains(setStatement)) return false;
        setStatements.put(container, setStatement);
        return true;
    }

    public Set<SetStatement> getSetStatements(SootMethod container){
        return setStatements.get(container);
    }

    public Set<GetStatement> getGetStatements(SootMethod container){
        return getStatements.get(container);
    }

    public boolean addGetStatement(SootMethod container, ParameterIndex baseIndex, Field field){
        GetStatement getStatement = new GetStatement(baseIndex, field);
        if(getStatements.get(container).contains(getStatement)) return false;
        getStatements.put(container, getStatement);
        return true;
    }

    @Override
    public void onNewPointsToSet(ValNode pointer, Set<AllocNode> newPts) {
        if(pointer instanceof ContextVarNode contextVarNode && contextVarNode.base() instanceof LocalVarNode var){
            processAbstractInstanceLoad(var, newPts);
            processAbstractInstanceStore(var, newPts);
        }
    }

    private void processAbstractInstanceLoad(LocalVarNode varWithoutCtx, Set<AllocNode> newPts){
        // x = y.f
        for(AbstractLoadField abstractLoadField: varToAbstractLoadFields.get(varWithoutCtx)){
            LocalVarNode lhs = abstractLoadField.getLoadedToVar();
            Field field = abstractLoadField.getField();
            if(PTAUtils.isConcerned(lhs.getType()) && field != null){
                Node csLhs = pta.parameterize(lhs, pta.emptyContext());
                for (AllocNode baseObj : newPts) {
                    if(Scene.v().getFastHierarchy().canStoreType(baseObj.getType(), field.getField().getDeclaringClass().getType())){
                        ValNode instanceField = PTAUtils.getCsInstanceField(pta, baseObj, field);
                        if(abstractLoadField.isNonRelay()){
                            this.nonRelayEdgeSrcToTgtWithOnlyRealParam.put(instanceField, csLhs);
                        }
                        solver.addIgnoreTypeFilterEdge(instanceField, csLhs);
                        pag.addEdge(instanceField, csLhs);
                    }
                }
            }
        }
    }
    private void processAbstractInstanceStore(LocalVarNode varWithoutCtx, Set<AllocNode> newPts){
        for(AbstractStoreField store: varToAbstractStoreFields.get(varWithoutCtx)){
            LocalVarNode rhs = store.getStoreFromVar();
            Field field = store.getField();
            if(PTAUtils.isConcerned(rhs.getType()) && field != null){
                Node csRhs = pta.parameterize(rhs, pta.emptyContext());
                for (AllocNode baseObj : newPts) {
                    if (Scene.v().getFastHierarchy().canStoreType(baseObj.getType(), field.getField().getDeclaringClass().getType())) {
                        ValNode instanceField = PTAUtils.getCsInstanceField(pta, baseObj, field);
                        pag.addEdge(csRhs, instanceField);
                    }
                }
            }
        }
    }

    @Override
    public void onNewMethod(SootMethod method) {
        if(method.isAbstract() || method.isPhantom() || !method.hasActiveBody()) return;
        String declaringClassName = method.getDeclaringClass().getName();

        MethodPAG methodPAG = pag.getMethodPAG(method);
        CSCMethodNodeFactory nodeFactory = (CSCMethodNodeFactory)methodPAG.nodeFactory();

        boolean[] metParam = new boolean[method.getParameterCount()];
        for (QueueReader<Node> reader = methodPAG.getInternalReader().clone(); reader.hasNext(); ) {
            Node from = reader.next();
            Node to = reader.next();
            if(to instanceof LocalVarNode leftLocalVar){
                if(from.equals(nodeFactory.caseThis())) continue;
                boolean isQilinParam = false;
                for (int i = 0; i < method.getParameterCount(); i++) {
                    if (!(method.getParameterType(i) instanceof RefLikeType)) {
                        continue;
                    }
                    if(from.equals(nodeFactory.caseParm(i))){
                        isQilinParam = true;
                        break;
                    }
                }
                if(isQilinParam) continue;
                leftDefinedVarsWithoutRealParam.add(leftLocalVar);
            }
        }

        if(!method.isStatic()){
            LocalVarNode realThis = nodeFactory.getRealThisVar();
            if(realThis != null){
                varToIndexWithOnlyRealParam.put(realThis, ParameterIndex.THISINDEX);
            }

        }

        for (int i = 0; i < method.getParameterCount(); i++) {
            if (!(method.getParameterType(i) instanceof RefLikeType)) {
                continue;
            }
            LocalVarNode realParam = nodeFactory.getRealParmVars(i);
            if(realParam == null) continue;
            if (!metParam[i]){
                metParam[i] = true;
                varToIndexWithOnlyRealParam.put(realParam, ParameterIndex.getRealParameterIndex(i));
            }else{
                throw new RuntimeException("duplicated param var detected!");
            }
        }





        if(declaringClassName.equals("java.awt.Component") || declaringClassName.equals("javax.swing.JComponent")) return;
        for (QueueReader<Node> reader = methodPAG.getInternalReader().clone(); reader.hasNext(); ) {
            Node from = reader.next();
            Node to = reader.next();
            if (from instanceof FieldRefNode fromFieldRef && to instanceof LocalVarNode lhs){
                // x = y.f
                LocalVarNode baseVar = (LocalVarNode) fromFieldRef.getBase();
                if(fromFieldRef.getField() instanceof Field field && PTAUtils.isConcerned(lhs.getType())){
                    ParameterIndex baseIndex = varToIndexWithOnlyRealParam.get(baseVar);
                    if(nodeFactory.isRealReturnVar(lhs) && baseIndex != null && !leftDefinedVarsWithoutRealParam.contains(baseVar)){
                        solver.putFieldLoadWithDisableRelay(fromFieldRef, lhs);
                        addBannedReturnVar(method, lhs, (LocalVarNode)nodeFactory.caseRet());
                        solver.addGetStmtEntry(method, baseIndex, field);
                    }
                }
            }
            else if (from instanceof LocalVarNode rhs && to instanceof FieldRefNode toFieldRef) {
                ContextMethod csMethod = (ContextMethod) pta.parameterize(method, pta.emptyContext());
                if(cgb.getCallEdgesToCallee(csMethod).stream().anyMatch(edge -> edge.src().equals(cgb.getFakeMain()))){
                    return;
                }

                // x.f = y
                if(toFieldRef.getField() instanceof Field field){
                    LocalVarNode baseVar = (LocalVarNode) toFieldRef.getBase();
                    if(PTAUtils.isConcerned(rhs.getType())){
                        ParameterIndex baseIndex = varToIndexWithOnlyRealParam.get(baseVar);
                        ParameterIndex rhsIndex = varToIndexWithOnlyRealParam.get(rhs);
                        if(baseIndex != null && rhsIndex != null && !leftDefinedVarsWithoutRealParam.contains(baseVar) && !leftDefinedVarsWithoutRealParam.contains(rhs)){
                            solver.addIgnoredStoreField(baseVar, field, rhs);
                            solver.addSetStmtEntry(method, field, baseIndex, rhsIndex);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onNewSetStatement(SootMethod method, Field field, ParameterIndex baseIndex, ParameterIndex rhsIndex) {
        if(addSetStatement(method, baseIndex, field , rhsIndex)){
            ContextMethod csMethod = (ContextMethod) pta.parameterize(method, pta.emptyContext());
            cgb.getCallEdgesToCallee(csMethod).forEach(edge -> {
                processSetStatementOnCallEdge(edge, field, baseIndex, rhsIndex);
            });
        }
    }

    private void processSetStatementOnCallEdge(Edge edge, Field field, ParameterIndex baseIndex, ParameterIndex rhsIndex){
        Value baseV = ParameterIndex.getCorrespondingArgument(edge, baseIndex);
        Value rhsV = ParameterIndex.getCorrespondingArgument(edge, rhsIndex);
        if(baseV != null && rhsV != null && PTAUtils.isConcerned(rhsV.getType())){
            SootMethod caller = edge.src();
            CSCMethodNodeFactory nodeFactory =(CSCMethodNodeFactory) pag.getMethodPAG(caller).nodeFactory();
            LocalVarNode base = (LocalVarNode) nodeFactory.getNode(baseV);
            LocalVarNode rhs = (LocalVarNode) nodeFactory.getNode(rhsV);
            ParameterIndex baseIndexAtCaller = varToIndexWithOnlyRealParam.get(base);
            ParameterIndex rhsIndexAtCaller = varToIndexWithOnlyRealParam.get(rhs);
            if(baseIndexAtCaller != null && rhsIndexAtCaller != null && !leftDefinedVarsWithoutRealParam.contains(base) && !leftDefinedVarsWithoutRealParam.contains(rhs)){
                solver.addSelectedMethod(edge.tgt());
                solver.addSetStmtEntry(caller, field, baseIndexAtCaller, rhsIndexAtCaller);
            }else{
                processNewAbstractStoreField(base, field, rhs);
            }
        }
    }

    private void processNewAbstractStoreField(LocalVarNode base, Field field, LocalVarNode rhs){
        Node csBase = pta.parameterize(base, pta.emptyContext());
        Node csRhs = pta.parameterize(rhs, pta.emptyContext());
        AbstractStoreField abstractStoreField = new AbstractStoreField(rhs, field, base);
        varToAbstractStoreFields.put(base, abstractStoreField);
        Iterator<AllocNode> basePtsItr = pta.reachingObjects(csBase).iterator();
        while (basePtsItr.hasNext()){
            AllocNode baseObj = basePtsItr.next();
            if(Scene.v().getFastHierarchy().canStoreType(baseObj.getType(), field.getField().getDeclaringClass().getType())){
                ValNode instanceField = PTAUtils.getCsInstanceField(pta, baseObj, field);
                pag.addEdge(csRhs, instanceField);
            }
        }
    }



    @Override
    public void onNewGetStatement(SootMethod method, ParameterIndex baseIndex, Field field) {
        if(addGetStatement(method, baseIndex, field)){
            ContextMethod csMethod = (ContextMethod) pta.parameterize(method, pta.emptyContext());
            for (Edge edge : cgb.getCallEdgesToCallee(csMethod)) {
                processGetStatementOnCallEdge(edge, baseIndex, field);
            }
        }
    }


    private void processGetStatementOnCallEdge(Edge edge, ParameterIndex baseIndex, Field field){
        Stmt stmt = edge.srcStmt();
        if(!(stmt instanceof AssignStmt methodCallWithRetValue)) return;
        InvokeExpr callSite = stmt.getInvokeExpr();
        SootMethod callee = edge.tgt();
        if(ContainerAccessHandler.cutReturnEdge(pag, callSite, callee)) return;
        Value lhsV = methodCallWithRetValue.getLeftOp();
        Value baseV = ParameterIndex.getCorrespondingArgument(edge, baseIndex);
        SootMethod caller = edge.src();
        CSCMethodNodeFactory nodeFactory = (CSCMethodNodeFactory)pag.getMethodPAG(caller).nodeFactory();
        LocalVarNode base = (LocalVarNode) nodeFactory.getNode(baseV);
        LocalVarNode lhs = (LocalVarNode) nodeFactory.getNode(lhsV);
        if (lhsV != null && base != null && baseV != null && lhs != null && PTAUtils.isConcerned(lhs.getType())) {
            ParameterIndex baseIndexAtCaller = varToIndexWithOnlyRealParam.get(base);
            solver.addSelectedMethod(callee);
            if (nodeFactory.isRealReturnVar(lhs) && baseIndexAtCaller != null) {
                addBannedReturnVar(caller, lhs, (LocalVarNode)nodeFactory.caseRet());
                solver.addGetStmtEntry(caller, baseIndexAtCaller, field);
                processNewAbstractLoadField(lhs, base, field, false);
            }
            else{
                processNewAbstractLoadField(lhs, base, field, true);
            }
        }
    }

    private void processNewAbstractLoadField(LocalVarNode lhs, LocalVarNode base, Field field, boolean terminate){
        Node csLhs = pta.parameterize(lhs, pta.emptyContext());
        Node csBase = pta.parameterize(base, pta.emptyContext());
        AbstractLoadField abstractLoadField = new AbstractLoadField(lhs, field, base, terminate);
        varToAbstractLoadFields.put(base, abstractLoadField);
        Iterator<AllocNode> basePtsItr = pta.reachingObjects(csBase).iterator();
        while (basePtsItr.hasNext()){
            AllocNode baseObj = basePtsItr.next();
            if(Scene.v().getFastHierarchy().canStoreType(baseObj.getType(), field.getField().getDeclaringClass().getType())){
                ValNode instanceField = PTAUtils.getCsInstanceField(pta, baseObj, field);
                if(!terminate){
                    nonRelayEdgeSrcToTgtWithOnlyRealParam.put(instanceField, csLhs);
                }
                solver.addIgnoreTypeFilterEdge(instanceField, csLhs);
                pag.addEdge(instanceField, csLhs);
            }
        }
    }



    public void processSetStatementOnNewCallEdge(Edge edge){
        SootMethod callee = edge.tgt();
        for (SetStatement setStatement : getSetStatements(callee)) {
            processSetStatementOnCallEdge(edge, setStatement.field(), setStatement.baseIndex(), setStatement.rhsIndex());
        }
    }

    public void processGetStatementOnNewCallEdge(Edge edge){
        SootMethod callee = edge.tgt();
        for (GetStatement getStatement : getGetStatements(callee)) {
            processGetStatementOnCallEdge(edge, getStatement.baseIndex(), getStatement.field());
        }
    }

    @Override
    public void onNewCallEdge(Edge edge) {
        SootMethod callee = edge.tgt();
        if(!callee.hasActiveBody()) return;
        InvokeExpr callSite = edge.srcStmt().getInvokeExpr();
        processSetStatementOnNewCallEdge(edge);
        if(edge.srcStmt() instanceof AssignStmt callWithRetVal){
            Value lhsV = callWithRetVal.getLeftOp();
            if(lhsV != null && PTAUtils.isConcerned(lhsV.getType())){
                processGetStatementOnNewCallEdge(edge);
                CSCMethodNodeFactory nodeFactory = (CSCMethodNodeFactory)pag.getMethodPAG(callee).nodeFactory();
                LocalVarNode lhs = (LocalVarNode) nodeFactory.getNode(lhsV);
                if(!ContainerAccessHandler.cutReturnEdge(pag, callSite, callee)){
                    for (LocalVarNode realReturnVar : nodeFactory.getRealReturnVars()) {
                        if(bannedRealReturnVars.contains(realReturnVar)){
                            VarNode csRet = (VarNode)pta.parameterize(realReturnVar, pta.emptyContext());
                            Node csLhs = pta.parameterize(lhs, pta.emptyContext());

                            for (Node pred : pag.predNodesOfCSVar(csRet)) {
                                if(!nonRelayEdgeSrcToTgtWithOnlyRealParam.get(pred).contains(csRet)){
                                    if(solver.isIgnoreTypeFieldEdge(pred, csRet)){
                                        solver.addIgnoreTypeFilterEdge(pred, csLhs);
                                    }
                                    pag.addEdge(pred, csLhs);
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Override
    public void onNewPFGEdge(Node src, Node tgt) {
        // !nonRelayEdgeSrcToTgt.get(src).contains(tgt) requires that nonRelayEdge has to be saved first before pag.addEdge(src, tgt)!!!!
        if(tgt instanceof ContextVarNode csVar && csVar.base() instanceof LocalVarNode base && bannedRealReturnVars.contains(base) && !nonRelayEdgeSrcToTgtWithOnlyRealParam.get(src).contains(tgt)){
            ContextMethod csMethod = (ContextMethod) pta.parameterize(base.getMethod(), pta.emptyContext());
            for (Edge edge : cgb.getCallEdgesToCallee(csMethod)) {
                SootMethod caller = edge.src();
                Stmt callSiteStmt = edge.srcStmt();
                if (callSiteStmt instanceof AssignStmt callSiteWithReturnValueStmt) {
                    CSCMethodNodeFactory nodeFactory = (CSCMethodNodeFactory)pag.getMethodPAG(caller).nodeFactory();
                    Value lhsV = callSiteWithReturnValueStmt.getLeftOp();
                    LocalVarNode lhs = (LocalVarNode) nodeFactory.getNode(lhsV);
                    if(lhs != null && PTAUtils.isConcerned(lhs.getType())){
                        Node csLhs = pta.parameterize(lhs, pta.emptyContext());
                        if(solver.isIgnoreTypeFieldEdge(src, tgt)){
                            solver.addIgnoreTypeFilterEdge(src, csLhs);
                        }
                        pag.addEdge(src, csLhs);
                    }
                }
            }
        }
    }



    private void addBannedReturnVar(SootMethod method, LocalVarNode realReturnVar, LocalVarNode qilinReturnVar){
        bannedRealReturnVars.add(realReturnVar);
        VarNode csRet = (VarNode)pta.parameterize(realReturnVar, pta.emptyContext());
        ContextMethod csMethod = (ContextMethod) pta.parameterize(method, pta.emptyContext());


        for (Edge edge : cgb.getCallEdgesToCallee(csMethod)) {
             Stmt callSiteStmt = edge.srcStmt();
             SootMethod caller = edge.src();
             if(callSiteStmt instanceof AssignStmt callSiteWithReturnValueStmt){
                 CSCMethodNodeFactory nodeFactory =(CSCMethodNodeFactory) pag.getMethodPAG(caller).nodeFactory();
                 Value lhsV = callSiteWithReturnValueStmt.getLeftOp();
                 LocalVarNode lhs = (LocalVarNode) nodeFactory.getNode(lhsV);
                 if(lhs != null && PTAUtils.isConcerned(lhs.getType())){
                     Node csLhs = pta.parameterize(lhs, pta.emptyContext());
                     for (Node pred : pag.predNodesOfCSVar(csRet)) {
                         if(!nonRelayEdgeSrcToTgtWithOnlyRealParam.get(pred).contains(csRet)){
                             if(solver.isIgnoreTypeFieldEdge(pred, csRet)){
                                 solver.addIgnoreTypeFilterEdge(pred, csLhs);
                             }
                             pag.addEdge(pred, csLhs);
                         }
                     }
                 }
             }
        }
        solver.addSpecialHandledRetVar(qilinReturnVar);
    }



    @Override
    public void onNewNonRelayLoadEdge(Node oDotF, Node to) {
        this.nonRelayEdgeSrcToTgtWithOnlyRealParam.put(oDotF, to);
    }
}
