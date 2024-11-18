package qilin.core.solver.csc.plugin.localflow;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import qilin.core.PTA;
import qilin.core.builder.CSCCallGraphBuilder;
import qilin.core.builder.CallGraphBuilder;
import qilin.core.builder.MethodNodeFactory;
import qilin.core.pag.*;
import qilin.core.solver.csc.CutShortcutSolver;
import qilin.core.solver.csc.plugin.Plugin;
import qilin.core.solver.csc.plugin.field.ParameterIndex;
import qilin.util.PTAUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.NumberedString;
import soot.util.queue.QueueReader;

import java.util.Iterator;
import java.util.Set;

public class LocalFlowHandler implements Plugin {
    private CutShortcutSolver solver;
    private PAG pag;
    private CSCCallGraphBuilder cgb;
    private PTA pta;
    private final MultiMap<SootMethod, ParameterIndexOrNewObj> directlyReturnParams = Maps.newMultiMap();
    public void setSolver(CutShortcutSolver solver){
        this.solver = solver;
        this.pag = solver.getPag();
        this.cgb = solver.getCgb();
        this.pta = solver.getPta();
    }


    @Override
    public void onNewPointsToSet(ValNode pointer, Set<AllocNode> newPts) {
    }

    @Override
    public void onNewMethod(SootMethod method) {
        if(method.isAbstract() || method.isPhantom()) return;
        Type type = method.getReturnType();
        if (!(type instanceof RefLikeType)) {
            return;
        }


        VarNode retNode = pag.getMethodPAG(method).nodeFactory().caseRet();
        MultiMap<LocalVarNode, ParameterIndexOrNewObj> result = getVariablesAssignedFromParameters(method);

        for (LocalVarNode localVarNode : result.keySet()) {
            if(!localVarNode.equals(retNode)) continue;
            for (ParameterIndexOrNewObj index : result.get(localVarNode)) {
                solver.addSelectedMethod(method);
                directlyReturnParams.put(method, index);
                solver.addSpecialHandledRetVar((LocalVarNode) retNode);
            }
        }

    }

    private MultiMap<LocalVarNode, ParameterIndexOrNewObj> getVariablesAssignedFromParameters(SootMethod method){
        MultiMap<LocalVarNode, ParameterIndexOrNewObj> result = Maps.newMultiMap();
        if(!method.hasActiveBody()) return result;
        MethodPAG methodPAG = pag.getMethodPAG(method);
        MethodNodeFactory nodeFactory = methodPAG.nodeFactory();

        MultiMap<LocalVarNode, Node> definitions = Maps.newMultiMap();
        for (QueueReader<Node> reader = methodPAG.getInternalReader().clone(); reader.hasNext(); ) {
            Node from = reader.next();
            Node to = reader.next();
            if(to instanceof LocalVarNode toLocalVar){
                definitions.put(toLocalVar, from);
            }
        }

        for (int i = 0; i < method.getParameterCount(); i++) {
            if (!(method.getParameterType(i) instanceof RefLikeType)) {
               continue;
            }
            LocalVarNode qilinParam = (LocalVarNode) nodeFactory.caseParm(i);
            if(PTAUtils.isConcerned(qilinParam.getType())){
                if(definitions.get(qilinParam).isEmpty()){
                    result.put(qilinParam, new ParameterIndexOrNewObj(false, ParameterIndex.getRealParameterIndex(i), null));
                }

            }
        }
        if(!method.isStatic()){
            LocalVarNode thisVar = (LocalVarNode) nodeFactory.caseThis();
            if(thisVar != null){
                result.put(thisVar, ParameterIndexOrNewObj.INDEX_THIS);
            }
        }

        boolean changed = true;
        int size = result.size();
        while(size > 0 && changed){

            for (LocalVarNode leftDefineToVar : definitions.keySet()) {
                boolean flag = true;
                // check pure local variable, only defined by params.
                for (Node rightDefineFromNode : definitions.get(leftDefineToVar)) {
                    if(rightDefineFromNode instanceof LocalVarNode localRightDefineFromVar){
                        if(!localRightDefineFromVar.getMethod().equals(method)) throw new RuntimeException("LocalVarNode from different method");
                        if(result.get(localRightDefineFromVar).isEmpty()){
                            flag = false;
                            break;
                        }
                    }else if(!(rightDefineFromNode instanceof AllocNode)){
                        flag = false;
                        break;
                    }
                }
                if(flag){
                    for (Node rightDefiningNode : definitions.get(leftDefineToVar)) {
                        if(rightDefiningNode instanceof LocalVarNode rightDefiningVar){
                            if(!rightDefiningVar.getMethod().equals(method)) throw new RuntimeException("LocalVarNode from different method");
                            result.get(rightDefiningVar).forEach(index -> result.put(leftDefineToVar, index));
                        }else if(rightDefiningNode instanceof AllocNode){
                            if(!(rightDefiningNode instanceof ContextAllocNode)){
                                rightDefiningNode = pta.parameterize(rightDefiningNode, pta.emptyContext());
                            }
                            result.put(leftDefineToVar, new ParameterIndexOrNewObj(true, null, (ContextAllocNode) rightDefiningNode));
                        }else {
                            throw new RuntimeException("Nor LocalVarNode nor AllocNode");
                        }
                    }
                }
            }
            changed = result.size() != size;
            size = result.size();
        }
        return result;

    }

    @Override
    public void onNewCallEdge(Edge edge) {
        MethodPAG srcmpag = pag.getMethodPAG(edge.src());
        MethodPAG tgtmpag = pag.getMethodPAG(edge.tgt());
        Stmt s = (Stmt) edge.srcUnit();
        InvokeExpr invokeExpr = s.getInvokeExpr();
        Context srcContext = edge.srcCtxt();
        MethodNodeFactory srcnf = srcmpag.nodeFactory();
        SootMethod callee = tgtmpag.getMethod();
        if(s instanceof AssignStmt methodCallWithRetValeStmt){
            Value dest = methodCallWithRetValeStmt.getLeftOp();
            if(!PTAUtils.isConcerned(dest.getType())) return;
            Node lhs = srcnf.getNode(dest);
            Node csLhs = pta.parameterize(lhs, pta.emptyContext());
            for (ParameterIndexOrNewObj indexOrNewObj : directlyReturnParams.get(callee)) {
                if(indexOrNewObj.isObj()){
                    // Here, we take the same operation like in Qilin.
                    pag.addEdge(indexOrNewObj.csObj(), csLhs);
                }
                else{
                    ParameterIndex index = indexOrNewObj.index();
                    if(index == ParameterIndex.THISINDEX && invokeExpr instanceof InstanceInvokeExpr instanceInvokeExpr){
                        Local base = (Local) instanceInvokeExpr.getBase();
                        VarNode csBase = cgb.getCSReceiverVarNode(base, edge.src());
                        // here we also align with in Qilin framework.
                        pag.addEdge(csBase, csLhs);
                    }

                    if(index != ParameterIndex.THISINDEX){
                        if(index == null) throw new RuntimeException("index is null");
                        Value arg = ParameterIndex.getCorrespondingArgument(edge, index);
                        if(arg != null && PTAUtils.isConcerned(arg.getType())){
                            Node argNode = srcnf.getNode(arg);
                            Node csArgNode = pta.parameterize(argNode, srcContext);
                            pag.addEdge(csArgNode, csLhs);
                        }
                    }
                }
            }
        }
    }
}
