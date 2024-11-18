package qilin.pta.toolkits.moon.BoxTraversal;

import pascal.taie.collection.MultiMap;
import pascal.taie.collection.Sets;
import qilin.core.PTA;
import qilin.core.pag.*;
import qilin.pta.toolkits.moon.FieldFlowRecorder;
import qilin.pta.toolkits.moon.Graph.VFG;
import qilin.pta.toolkits.moon.Graph.FieldEdge;
import qilin.pta.toolkits.moon.Graph.FlowEdge;
import qilin.pta.toolkits.moon.MatchCaseRecorder;
import qilin.pta.toolkits.moon.KeyTypeCollector;
import qilin.pta.toolkits.moon.MoonGraphBuilder;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.jimple.spark.pag.SparkField;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.Stack;

public class MatchObjTraversal {
    private static final int fieldTypeThreshold = 3;
    private final VFG VFGForHeap;
    private final PTA ciResult;
    private final KeyTypeCollector keyTypeCollector;
    private final FieldFlowRecorder fieldFlowRecorder;
    private MatchCaseRecorder matchCaseRecorder;
    private final MultiMap<SootMethod, AllocNode> methodToInvokeObjs;
    public MatchObjTraversal(PTA ciResult, MoonGraphBuilder graphBuilder){
        this.keyTypeCollector = graphBuilder.getKeyTypeCollector();
        this.fieldFlowRecorder = graphBuilder.getFieldFlowRecorder();
        this.VFGForHeap = graphBuilder.getVFGForHeap();
        this.methodToInvokeObjs = graphBuilder.getMethodToRecvObjs();
        this.ciResult = ciResult;
    }

    public void setMatchCaseRecorder(MatchCaseRecorder matchCaseRecorder) {
        this.matchCaseRecorder = matchCaseRecorder;
    }

    public boolean canBeSeperated(AllocNode obj){
        SootMethod inMethod = obj.getMethod();
        if(inMethod == null) return false;

        if(canReturnOut(obj)){
            if(matchCaseRecorder.isMetParamOfAllocatedMethod(obj) || inMethod.isStatic()){
                return true;
            }
        }

        VarNode thisVar =  ciResult.getPag().getMethodPAG(inMethod).nodeFactory().caseThis();
        Set<SootMethod> traversalMethods = Sets.newSet();
        Set<Node> thisAlias = getAliasOf(thisVar, traversalMethods);
        // first check if the obj flows into this field
        // then check if the obj flows out of the allocated method.

        Deque<ForwardTrace> stack = new ArrayDeque<>();
        Set<ForwardTrace> visited = Sets.newSet();
        stack.push(new ForwardTrace(obj, Traversal.Allocation));

        Set<Type> fieldTypes = Sets.newSet();
        while(!stack.isEmpty()){
            ForwardTrace crtTrace = stack.pop();
            if(visited.contains(crtTrace)){
                continue;
            }
            visited.add(crtTrace);
            Node crtNode = crtTrace.getNode();
            Traversal crtState = crtTrace.getState();


            for (FlowEdge outEdge : VFGForHeap.getSuccsOf(crtNode)) {
                FlowKind flowKind = outEdge.flowKind();
                Traversal nextState = moveToNode(crtState, flowKind, true);
                if(nextState == Traversal.UnDef) continue;
                if(flowKind == FlowKind.FIELD_STORE && thisAlias.contains(outEdge.target())){
                    FieldEdge fieldEdge = (FieldEdge) outEdge;
                    fieldTypes.add(fieldEdge.field().getType());
                    if(isContextAwareField(inMethod, fieldEdge.field())){
                        return fieldTypes.size() <= fieldTypeThreshold;
                    }
                }else{
                    if(flowKind != FlowKind.FIELD_STORE || keyTypeCollector.isConcernedType(((FieldEdge)outEdge).field().getType())){
                        if(outEdge.target() instanceof LocalVarNode localVarNode) {
                        if (traversalMethods.contains(localVarNode.getMethod())) {
                            if(outEdge instanceof FieldEdge && flowKind == FlowKind.FIELD_STORE){
                                fieldTypes.add(((FieldEdge) outEdge).field().getType());
                            }
                            stack.push(new ForwardTrace(localVarNode, nextState));
                        }
                        }else{
                            stack.push(new ForwardTrace(outEdge.target(), nextState));
                        }
                    }
                }
            }
            for (FlowEdge inEdge : VFGForHeap.getPredsOf(crtNode)) {
                FlowKind flowKind = inEdge.flowKind();
                Traversal nextState = moveToNode(crtState, flowKind, false);
                if(nextState == Traversal.UnDef) continue;
                if(flowKind == FlowKind.FIELD_LOAD && thisAlias.contains(inEdge.source())){
                    FieldEdge fieldEdge = (FieldEdge) inEdge;
                    fieldTypes.add(fieldEdge.field().getType());
                    if(isContextAwareField(inMethod, fieldEdge.field())) {
                        return fieldTypes.size() <= fieldTypeThreshold;
                    }
                }else{
                    if(flowKind != FlowKind.FIELD_LOAD || keyTypeCollector.isConcernedType(((FieldEdge)inEdge).field().getType())){
                        if(inEdge.source() instanceof LocalVarNode localVarNode) {
                        if (traversalMethods.contains(localVarNode.getMethod())) {
                            if(inEdge instanceof FieldEdge && flowKind == FlowKind.FIELD_LOAD){
                                fieldTypes.add(((FieldEdge) inEdge).field().getType());
                            }
                            stack.push(new ForwardTrace(localVarNode, nextState));
                        }
                        }else{
                            stack.push(new ForwardTrace(inEdge.source(), nextState));
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isContextAwareField(SootMethod method, SparkField field){
        if(!keyTypeCollector.isConcernedType(field.getType())) return false;
        Set<AllocNode> recvObjs = methodToInvokeObjs.get(method);
        for (AllocNode recvObj : recvObjs) {
            if(recvObj.getType() instanceof RefType){
                if(fieldFlowRecorder.isConnceredField(recvObj, field)) return true;
            }
        }
        return false;
    }

    private Traversal moveToNode(Traversal crtState, FlowKind flowKind, boolean forward){
        switch (crtState){
            case Allocation -> {
                if(forward && flowKind == FlowKind.NEW){
                    return Traversal.DirectVar;
                }
            }

            case DirectVar -> {
                if(forward && flowKind == FlowKind.LOCAL_ASSIGN){
                    return Traversal.DirectVar;
                }else if(forward && flowKind == FlowKind.FIELD_STORE){
                    return Traversal.StoredInVar;
                }
            }

            case StoredInVar -> {
                if(forward){
                    // Forward
                    if(flowKind == FlowKind.FIELD_STORE){
                        return Traversal.StoredInVar;
                    }else if(flowKind == FlowKind.LOCAL_ASSIGN){
                        return Traversal.StoredInVar;
                    }
                }
                else{
                    // backForward
                    if(flowKind == FlowKind.LOCAL_ASSIGN){
                        return Traversal.StoredInVar;
                    }else if(flowKind == FlowKind.FIELD_LOAD){
                        return Traversal.StoredInVar;
                    }
                }
            }
        }
        return Traversal.UnDef;
    }


    private boolean canReturnOut(AllocNode obj){
        Stack<Node> stack = new Stack<>();
        for (FlowEdge edge : VFGForHeap.getSuccsOf(obj)) {
            stack.push(edge.target());
        }
        for (FlowEdge edge : VFGForHeap.getPredsOf(obj)) {
            stack.push(edge.source());
        }
        Set<Node> visited = Sets.newSet();
        while(!stack.isEmpty()){
            Node crtNode = stack.pop();
            visited.add(crtNode);
            for (FlowEdge outEdge : VFGForHeap.getSuccsOf(crtNode)) {
                if(outEdge.target() instanceof LocalVarNode localVarNode && localVarNode.isReturn() && localVarNode.getMethod().equals(obj.getMethod())){
                    return true;
                }
                if(outEdge.flowKind() == FlowKind.LOCAL_ASSIGN && !visited.contains(outEdge.target())){
                    stack.push(outEdge.target());
                }
            }

        }
        return false;

    }


    private Set<Node> getAliasOf(Node node, Set<SootMethod> traversalMethods){
        Stack<Node> stack = new Stack<>();
        for (FlowEdge edge : VFGForHeap.getSuccsOf(node)) {
            stack.push(edge.target());
        }
        for (FlowEdge inEdge : VFGForHeap.getPredsOf(node)) {
            stack.push(inEdge.source());
        }
        Set<Node> visited = Sets.newSet();
        while(!stack.isEmpty()){
            Node crtNode = stack.pop();
            visited.add(crtNode);
            if(crtNode instanceof LocalVarNode localVarNode){
                SootMethod method = localVarNode.getMethod();
                if(method != null){
                    traversalMethods.add(method);
                }

            }
            for (FlowEdge outEdge : VFGForHeap.getSuccsOf(crtNode)) {
                if(outEdge.flowKind() == FlowKind.LOCAL_ASSIGN && !visited.contains(outEdge.target())){
                    stack.push(outEdge.target());
                }
            }
        }
        return visited;
    }
}
