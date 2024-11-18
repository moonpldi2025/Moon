/* Qilin - a Java Pointer Analysis Framework
 * Copyright (C) 2021-2030 Qilin developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3.0 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <https://www.gnu.org/licenses/lgpl-3.0.en.html>.
 */

package qilin.core.builder;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import qilin.CoreConfig;
import qilin.core.PTA;
import qilin.core.PTAScene;
import qilin.core.pag.*;
import qilin.core.sets.P2SetVisitor;
import qilin.core.sets.PointsToSetInternal;
import qilin.core.solver.csc.plugin.Plugin;
import qilin.core.solver.csc.plugin.container.ContainerAccessHandler;
import qilin.util.DataFactory;
import qilin.util.PTAUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.tagkit.LineNumberTag;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;

import java.util.*;

public class CSCCallGraphBuilder extends CallGraphBuilder {
    protected final Map<VarNode, Collection<VirtualCallSite>> receiverToSites;
    protected final Map<SootMethod, Map<Object, Stmt>> methodToInvokeStmt;
    protected final Set<MethodOrMethodContext> reachMethods;
    private ChunkedQueue<MethodOrMethodContext> newRMQueue;

    protected final Set<Edge> calledges;
    protected final MultiMap<ContextMethod, Edge> calleeToEdges = Maps.newMultiMap();
    protected final MultiMap<InvokeExpr, SootMethod> callSitesToCallee = Maps.newMultiMap();
    protected final PTA pta;
    protected final CSCPAG pag;
    protected CallGraph cicg;

    private MultiMap<Node, Node> returnEdges;
    private Plugin plugin;
    private Set<InvokeExpr> recoveredCallSites;
    private Set<LocalVarNode> qilinReturnVarsSpeciallyHandled;
    private final Map<InvokeExpr, Integer> invokeToLine = Maps.newMap();

    public Map<InvokeExpr, Integer> getInvokeToLine() {
        return invokeToLine;
    }

    public void setQilinReturnVarsSpeciallyHandled(Set<LocalVarNode> qilinReturnVarsSpeciallyHandled) {
        this.qilinReturnVarsSpeciallyHandled = qilinReturnVarsSpeciallyHandled;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public void setReturnEdges(MultiMap<Node, Node> returnEdges) {
        this.returnEdges = returnEdges;
    }

    public void setRecoveredCallSites(Set<InvokeExpr> recoveredCallSites) {
        this.recoveredCallSites = recoveredCallSites;
    }

    public CSCCallGraphBuilder(PTA pta) {
        super(pta);
        this.pta = pta;
        this.pag = (CSCPAG) pta.getPag();
        PTAScene.v().setCallGraph(new CallGraph());
        receiverToSites = DataFactory.createMap(PTAScene.v().getLocalNumberer().size());
        methodToInvokeStmt = DataFactory.createMap();
        reachMethods = DataFactory.createSet();
        calledges = DataFactory.createSet();
    }

    public void setRMQueue(ChunkedQueue<MethodOrMethodContext> rmQueue) {
        this.newRMQueue = rmQueue;
    }

    public Collection<MethodOrMethodContext> getReachableMethods() {
        return reachMethods;
    }

    // initialize the receiver to sites map with the number of locals * an
    // estimate for the number of contexts per methods
    public Map<VarNode, Collection<VirtualCallSite>> getReceiverToSitesMap() {
        return receiverToSites;
    }

    public Collection<VirtualCallSite> callSitesLookUp(VarNode receiver) {
        return receiverToSites.getOrDefault(receiver, Collections.emptySet());
    }

    public CallGraph getCallGraph() {
        if (cicg == null) {
            constructCallGraph();
        }
        return PTAScene.v().getCallGraph();
    }

    public CallGraph getCICallGraph() {
        if (cicg == null) {
            constructCallGraph();
        }
        return cicg;
    }

    private void constructCallGraph() {
        cicg = new CallGraph();
        Map<Unit, Map<SootMethod, Set<SootMethod>>> map = DataFactory.createMap();
        calledges.forEach(e -> {
            // PTAScene.v().getCallGraph().addEdge(e);
            SootMethod src = e.src();
            SootMethod tgt = e.tgt();
            Unit unit = e.srcUnit();
            Map<SootMethod, Set<SootMethod>> submap = map.computeIfAbsent(unit, k -> DataFactory.createMap());
            Set<SootMethod> set = submap.computeIfAbsent(src, k -> DataFactory.createSet());
            if (set.add(tgt)) {
                cicg.addEdge(new Edge(src, e.srcUnit(), tgt, e.kind()));
            }
        });
    }

    public List<MethodOrMethodContext> getEntryPoints() {
        Node thisRef = pag.getMethodPAG(PTAScene.v().getFakeMainMethod()).nodeFactory().caseThis();
        thisRef = pta.parameterize(thisRef, pta.emptyContext());
        pag.addEdge(pta.getRootNode(), thisRef);
        return Collections.singletonList(pta.parameterize(PTAScene.v().getFakeMainMethod(), pta.emptyContext()));
    }

    public SootMethod getFakeMain(){
        return PTAScene.v().getFakeMainMethod();
    }
    public void initReachableMethods() {
        for (MethodOrMethodContext momc : getEntryPoints()) {
            if (reachMethods.add(momc)) {
                newRMQueue.add(momc);
            }
        }
    }

    public VarNode getCSReceiverVarNode(Local receiver, MethodOrMethodContext m) {
        LocalVarNode base = pag.makeLocalVarNode(receiver, receiver.getType(), m.method());
        return (VarNode) pta.parameterize(base, pta.emptyContext());
    }

    protected void dispatch(AllocNode receiverNode, VirtualCallSite site) {
        Type type = receiverNode.getType();
        final QueueReader<SootMethod> targets = PTAUtils.dispatch(type, site);
        while (targets.hasNext()) {
            SootMethod target = targets.next();
            if (site.iie() instanceof SpecialInvokeExpr) {
                Type calleeDeclType = target.getDeclaringClass().getType();
                if (!Scene.v().getFastHierarchy().canStoreType(type, calleeDeclType)) {
                    continue;
                }
            }
            addVirtualEdge(site.container(), site.getUnit(), target, site.kind(), receiverNode);
        }
    }

    private void addVirtualEdge(MethodOrMethodContext caller, Unit callStmt, SootMethod callee, Kind kind, AllocNode receiverNode) {
        Context tgtContext = pta.createCalleeCtx(caller, receiverNode, new CallSite(callStmt), callee);
        MethodOrMethodContext cstarget = pta.parameterize(callee, tgtContext);
        handleCallEdge(new Edge(caller, callStmt, cstarget, kind));
        Node thisRef = pag.getMethodPAG(callee).nodeFactory().caseThis();
        thisRef = pta.parameterize(thisRef, cstarget.context());

        // NOTE: here, qilin add an edge from receiver node to thisref....
        pag.addEdge(receiverNode, thisRef);
        MethodCallDetail.v().addCalleeToCtxAndCaller(callee, receiverNode, caller.method());
    }

    public void injectCallEdge(Object heapOrType, MethodOrMethodContext callee, Kind kind) {
        Map<Object, Stmt> stmtMap = methodToInvokeStmt.computeIfAbsent(callee.method(), k -> DataFactory.createMap());
        if (!stmtMap.containsKey(heapOrType)) {
            InvokeExpr ie = new JStaticInvokeExpr(callee.method().makeRef(), Collections.emptyList());
            JInvokeStmt stmt = new JInvokeStmt(ie);
            stmtMap.put(heapOrType, stmt);
            handleCallEdge(new Edge(pta.parameterize(PTAScene.v().getFakeMainMethod(), pta.emptyContext()), stmtMap.get(heapOrType), callee, kind));
        }
    }

    public void addStaticEdge(MethodOrMethodContext caller, Unit callStmt, SootMethod calleem, Kind kind) {
        Context typeContext = pta.createCalleeCtx(caller, null, new CallSite(callStmt), calleem);
        MethodOrMethodContext callee = pta.parameterize(calleem, typeContext);
        handleCallEdge(new Edge(caller, callStmt, callee, kind));
        MethodCallDetail.v().addCalleeToCtxAndCaller(calleem, MethodCallDetail.STATIC_OBJ_CTX, caller.method());
    }


    public void addInvokeToLine(Stmt callStmt){
        if(callStmt.getTag("LineNumberTag") != null){
            InvokeExpr invokeExpr = callStmt.getInvokeExpr();
            int lineNumber = ((LineNumberTag) callStmt.getTag("LineNumberTag")).getLineNumber();
            int existingLine = invokeToLine.getOrDefault(invokeExpr, -1);
            if(invokeToLine.containsKey(invokeExpr) && existingLine != lineNumber) {
                throw new RuntimeException("Different line number for the same invokeExpr.");
            }
            invokeToLine.put(invokeExpr, lineNumber);
        }
    }
    protected void handleCallEdge(Edge edge) {
        if (calledges.add(edge)) {
            addInvokeToLine(edge.srcStmt());
            ContextMethod callee = (ContextMethod)edge.getTgt();
            callSitesToCallee.put(edge.srcStmt().getInvokeExpr(), edge.tgt());
            calleeToEdges.put(callee, edge);
            if (reachMethods.add(callee)) {
                newRMQueue.add(callee);
                plugin.onNewMethod(callee.method());
            }
            processCallAssign(edge);
            plugin.onNewCallEdge(edge);
        }
    }

    public Set<Edge> getCallEdgesToCallee(ContextMethod callee){
        return calleeToEdges.get(callee);
    }

    public Set<SootMethod> getCalleesOfCallSite(InvokeExpr invokeExpr){
        return callSitesToCallee.get(invokeExpr);
    }

    public boolean recordVirtualCallSite(VarNode receiver, VirtualCallSite site) {
        Collection<VirtualCallSite> sites = receiverToSites.computeIfAbsent(receiver, k -> DataFactory.createSet());
        return sites.add(site);
    }

    public void virtualCallDispatch(PointsToSetInternal p2set, VirtualCallSite site) {
        p2set.forall(new P2SetVisitor(pta) {
            public void visit(Node n) {
                dispatch((AllocNode) n, site);
            }
        });
    }

    /**
     * Adds method target as a possible target of the invoke expression in s. If
     * target is null, only creates the nodes for the call site, without actually
     * connecting them to any target method.
     **/
    private void processCallAssign(Edge e) {
        MethodPAG srcmpag = pag.getMethodPAG(e.src());
        MethodPAG tgtmpag = pag.getMethodPAG(e.tgt());
        Stmt s = (Stmt) e.srcUnit();
        Context srcContext = e.srcCtxt();
        Context tgtContext = e.tgtCtxt();
        MethodNodeFactory srcnf = srcmpag.nodeFactory();
        MethodNodeFactory tgtnf = tgtmpag.nodeFactory();
        SootMethod tgtmtd = tgtmpag.getMethod();
        InvokeExpr ie = s.getInvokeExpr();
        // add arg --> param edges.
        int numArgs = ie.getArgCount();
        for (int i = 0; i < numArgs; i++) {
            Value arg = ie.getArg(i);
            if (!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant) {
                continue;
            }
            Type tgtType = tgtmtd.getParameterType(i);
            if (!(tgtType instanceof RefLikeType)) {
                continue;
            }
            Node argNode = srcnf.getNode(arg);
            argNode = pta.parameterize(argNode, srcContext);
            Node parm = tgtnf.caseParm(i);
            parm = pta.parameterize(parm, tgtContext);
            pag.addEdge(argNode, parm);

            Stmt callStmt = e.srcStmt();
            InvokeExpr invokeExpr;
            if(callStmt instanceof JInvokeStmt invokeStmt){
                invokeExpr = invokeStmt.getInvokeExpr();
            }else if(callStmt instanceof JAssignStmt assignStmt){
                invokeExpr = assignStmt.getInvokeExpr();
            }else {
                throw new RuntimeException("Unsupported callStmt type.");
            }

            Value v = null;
            if(invokeExpr instanceof JVirtualInvokeExpr virtualInvokeExpr){
                v = virtualInvokeExpr.getBase();
            }else if(invokeExpr instanceof JSpecialInvokeExpr specialInvokeExpr){
                v = specialInvokeExpr.getBase();
            }else if(invokeExpr instanceof JInterfaceInvokeExpr interfaceInvokeExpr){
                v = interfaceInvokeExpr.getBase();
            }else if(invokeExpr instanceof StaticInvokeExpr){
            }
            else{
                throw new RuntimeException("Unsupported invokeExpr type.");
            }
            
            if(v != null){
                MethodCallDetail.v().addArgToParamToRecvValue(argNode, parm, v);
            }



        }
        // add normal return edge
        if (s instanceof AssignStmt) {
            if(!ContainerAccessHandler.cutReturnEdge(pag, ie, e.tgt()) || recoveredCallSites.contains(ie)) {
                Value lhsV = ((AssignStmt) s).getLeftOp();
                if (lhsV.getType() instanceof RefLikeType) {
                    LocalVarNode lhs = (LocalVarNode) srcnf.getNode(lhsV);
                    Node csLhs = pta.parameterize(lhs, srcContext);
                    Type type = e.tgt().getReturnType();
                    if (type instanceof RefLikeType) {
                        LocalVarNode retNode = (LocalVarNode) tgtnf.caseRet();

                        if (tgtmtd.getReturnType() instanceof RefLikeType && !qilinReturnVarsSpeciallyHandled.contains(retNode)){
//                            if(e.tgt().toString().contains("select")){
//                                System.out.println("#solverAddingReturnEdge: " + e.src() + "\t->\t" + e.tgt());
//                            }
                            Node csRet = pta.parameterize(retNode, tgtContext);
                            returnEdges.put(csRet, csLhs);
                            pag.addEdge(csRet, csLhs);
                        }
                    }

                }
            }
        }
        // add throw return edge
        if (CoreConfig.v().getPtaConfig().preciseExceptions) {
            Node throwNode = tgtnf.caseMethodThrow();
            /*
             * If an invocation statement may throw exceptions, we create a special local variables
             * to receive the exception objects.
             * a_ret = x.foo(); here, a_ret is a variable to receive values from return variables of foo();
             * a_throw = x.foo(); here, a_throw is a variable to receive exception values thrown by foo();
             * */
            throwNode = pta.parameterize(throwNode, tgtContext);
            MethodNodeFactory mnf = srcmpag.nodeFactory();
            Node dst = mnf.makeInvokeStmtThrowVarNode(s, srcmpag.getMethod());
            dst = pta.parameterize(dst, srcContext);
            pag.addEdge(throwNode, dst);
        }
    }
}
