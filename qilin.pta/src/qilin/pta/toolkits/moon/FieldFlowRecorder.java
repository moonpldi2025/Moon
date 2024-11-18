package qilin.pta.toolkits.moon;

import pascal.taie.collection.*;
import qilin.core.PTA;
import qilin.core.builder.MethodNodeFactory;
import qilin.core.pag.*;
import qilin.pta.toolkits.moon.Graph.VFG;
import qilin.pta.toolkits.moon.Graph.FieldEdge;
import qilin.pta.toolkits.moon.Graph.FlowEdge;
import qilin.util.PTAUtils;
import soot.RefLikeType;
import soot.SootMethod;

import soot.jimple.spark.pag.SparkField;

import java.util.*;
import java.util.stream.Collectors;

public class FieldFlowRecorder {


    protected final MultiMap<SparkField, LocalVarNode> fieldToInParams = Maps.newConcurrentMultiMap();
    protected final MultiMap<SparkField, LocalVarNode> fieldToOutParams = Maps.newConcurrentMultiMap();


    protected final TwoKeyMultiMap<AllocNode, SparkField, VarNode> objToNonThisFieldStore = Maps.newConcurrentTwoKeyMultiMap();
    protected final TwoKeyMultiMap<AllocNode, SparkField, VarNode> objToNonThisFieldLoad = Maps.newConcurrentTwoKeyMultiMap();
    protected final Set<SparkField> hasNonThisFieldLoad = Sets.newConcurrentSet();
    protected final MultiMap<AllocNode, LocalVarNode> objToArgOfInvokeMethods = Maps.newConcurrentMultiMap();
    private final FieldRecorder fieldRecorder;
    private final VFG VFGForHeap;
    private final PTA pta;
    private final MultiMap<AllocNode, SootMethod> objToInvokeMethods;
    public FieldFlowRecorder(PTA pta, FieldRecorder fieldRecorder, VFG VFGForHeap, MultiMap<AllocNode, SootMethod> objToInvokeMethods){
        this.pta = pta;
        this.fieldRecorder = fieldRecorder;
        this.VFGForHeap = VFGForHeap;
        this.objToInvokeMethods = objToInvokeMethods;
    }

    public void build(KeyTypeCollector openTypeCollector){

            fieldRecorder.allFields(openTypeCollector)
                    .parallelStream()
                    .forEach(field -> {
                        boolean needInCheck = false;
                        Set<LocalVarNode> retOrParams = traversal( field, false);
                        if (!retOrParams.isEmpty()) {
                            needInCheck = true;
                            fieldToOutParams.putAll(field, retOrParams);
                        }
                        needInCheck |= hasNonThisFieldLoad.contains(field);
                        if(needInCheck){
                            Set<LocalVarNode> paramsOrThis = traversal( field, true);
                            if (!paramsOrThis.isEmpty()) {
                                fieldToInParams.putAll(field, paramsOrThis);
                            }
                        }
                    });

            objToInvokeMethods.keySet().parallelStream().forEach(obj -> {
                Set<SootMethod> invokeMethods = objToInvokeMethods.get(obj);
                invokeMethods.forEach(m -> {
                    MethodNodeFactory factory = pta.getPag().getMethodPAG(m).nodeFactory();
                    for (int i = 0; i < m.getParameterCount(); ++i) {
                        if (m.getParameterType(i) instanceof RefLikeType && !PTAUtils.isPrimitiveArrayType(m.getParameterType(i))) {
                            LocalVarNode param = (LocalVarNode) factory.caseParm(i);
                            objToArgOfInvokeMethods.put(obj, param);
                        }
                    }
                    objToArgOfInvokeMethods.put(obj, (LocalVarNode) factory.caseThis());
                });
            });



    }


    private boolean hasInflow(AllocNode heap, SparkField field){
        if(objToNonThisFieldStore.containsKey(heap, field)) return true;
        return Sets.haveOverlap(fieldToInParams.get(field), objToArgOfInvokeMethods.get(heap));
    }

    private boolean hasOutflow(AllocNode heap, SparkField field) {
        if (objToNonThisFieldLoad.containsKey(heap, field)) return true;
        Set<SootMethod> invokedMethods = objToInvokeMethods.get(heap);
        return fieldToOutParams.get(field).stream().anyMatch(v -> invokedMethods.contains(v.getMethod()));
    }

    public boolean isConnceredField(AllocNode heap, SparkField field){
        return hasInflow(heap, field) && hasOutflow(heap, field);
    }

    private Set<LocalVarNode> traversal(SparkField field, boolean isInflow) {
        Set<LocalVarNode> ret = new HashSet<>();
        MultiMap<Traversal, Node> state2nodes = Maps.newMultiMap();
        Stack<Pair<Node, Traversal>> stack = new Stack<>();

        Set<SootMethod> inMethods = fieldRecorder.fieldToBaseObjs.get(field).stream().map(objToInvokeMethods::get).flatMap(Set::stream).collect(Collectors.toSet());

        VFGForHeap.getThisVars().stream().filter(thisVar -> {
            SootMethod method = thisVar.getMethod();
            return inMethods.contains(method);
        }).forEach(thisVar -> stack.push(Pair.of(thisVar, Traversal.ThisAlias)));

        while (!stack.isEmpty()) {
            Pair<Node, Traversal> front = stack.pop();
            if (front.second() == Traversal.Finish) {
                if (front.first() instanceof LocalVarNode lvn) {
                    ret.add(lvn);
                }
            }
            state2nodes.put(front.second(), front.first());
            Set<Pair<Node, Traversal>> nexts = move(front, field, isInflow);
            for (Pair<Node, Traversal> nodeState : nexts) {
                if (!state2nodes.get(nodeState.second()).contains(nodeState.first())) {
                    stack.add(nodeState);
                }
            }
        }
        return ret;
    }


    private Set<Pair<Node, Traversal>> move(Pair<Node, Traversal> nodeState, SparkField field, boolean in) {
        Node node = nodeState.first();
        Traversal state = nodeState.second();
        Set<Pair<Node, Traversal>> ret = new HashSet<>();

        for (FlowEdge outEdge : VFGForHeap.getSuccsOf(node)) {
            boolean metTargetField = false;
            if(outEdge instanceof FieldEdge fieldEdge){
                metTargetField = fieldEdge.field().equals(field);
            }

            Traversal next;
            if (in) {
                next = moveForIn(state, outEdge.flowKind(), metTargetField, true);
            } else {
                next = moveForOut(state, outEdge.flowKind(), metTargetField, true);
            }

            if (next != Traversal.Undef){
                ret.add(Pair.of(outEdge.target(), next));
            }

        }

        for (FlowEdge inEdge : VFGForHeap.getPredsOf(node)) {

            boolean metTargetField = false;
            if(inEdge instanceof FieldEdge fieldEdge){
                metTargetField = fieldEdge.field().equals(field);
            }
            Traversal nextState;
            if (in) {
                nextState = moveForIn(state, inEdge.flowKind(), metTargetField, false);
            } else {
                nextState = moveForOut(state, inEdge.flowKind(), metTargetField, false);
            }
            if (nextState != Traversal.Undef){
                ret.add(Pair.of(inEdge.source(), nextState));
            }

        }

        return ret;
    }

    /*
     * This method describes another automaton and its transition function for checking whether the field value could be
     * loaded to any method's return.
     * Correspoing to the automaton given in Fig 7c in the paper.
     * */
    private Traversal moveForOut(Traversal currState, FlowKind kind, boolean fieldMatch, boolean isForward) {
        switch (currState) {
            case THIS -> {
                if(!isForward && kind == FlowKind.THIS_CONNECT){
                    return Traversal.ThisAlias;
                }
            }
            case ThisAlias -> {
                if(isForward) {
                    if (kind == FlowKind.LOCAL_ASSIGN) {
                        return Traversal.ThisAlias;
                    } else if (kind == FlowKind.FIELD_LOAD) {
                        if (fieldMatch) {
                            return Traversal.DirectVar;
                        }
                    }
                }
            }
            case DirectVar -> {
                if(isForward){
                    if(kind == FlowKind.LOCAL_ASSIGN || kind == FlowKind.FIELD_LOAD || kind == FlowKind.CALL_LOAD ){
                        return Traversal.DirectVar;
                    }else if(kind == FlowKind.RETURN) {
                        return Traversal.Finish;
                    }else if(kind == FlowKind.FIELD_STORE || kind == FlowKind.CALL_STORE){
                        return Traversal.StoredInVar;
                    }
                }else{
                    if(kind == FlowKind.FIELD_STORE || kind == FlowKind.CALL_STORE ){
                        return Traversal.StoredInVar;
                    }
                }
            }
            case StoredInVar -> {
                if(!isForward){
                    if(kind == FlowKind.LOCAL_ASSIGN || kind == FlowKind.FIELD_LOAD || kind == FlowKind.CALL_LOAD){
                        return Traversal.StoredInVar;
                    }else if(kind == FlowKind.NEW) {
                        return Traversal.Allocation;
                    }
                }else{
                    if(kind == FlowKind.PARAMETER_PASSING){
                        return Traversal.Finish;
                    }
                }
            }
            case Allocation -> {
                if(isForward && kind == FlowKind.NEW){
                    return Traversal.DirectVar;
                }
            }
        }
        return Traversal.Undef;
    }


    /*
     * This method describes an automaton and its transition function for checking whether the value could be stored
     * into any field.
     * Correspoing to the automaton given in Fig 7b in the paper.
     * */
    private Traversal moveForIn(Traversal currState, FlowKind kind, boolean fieldMatch, boolean isForward) {
        switch (currState) {
            case Allocation -> {
                if(isForward && kind == FlowKind.NEW){
                    return Traversal.DirectVar;
                }
            }

            case THIS -> {
                if(!isForward && kind == FlowKind.THIS_CONNECT){
                    return Traversal.ThisAlias;
                }
            }
            case DirectVar -> {
                if(isForward){
                    if(kind == FlowKind.LOCAL_ASSIGN || kind == FlowKind.FIELD_LOAD || kind == FlowKind.CALL_LOAD ){
                        return Traversal.DirectVar;
                    }else if(kind == FlowKind.FIELD_STORE || kind == FlowKind.CALL_STORE) {
                        return Traversal.StoredInVar;
                    }
                }else{
                    if(kind == FlowKind.FIELD_STORE || kind == FlowKind.CALL_STORE){
                        return Traversal.StoredInVar;
                    }
                }
            }
            case StoredInVar -> {

                if(!isForward){
                    if(kind == FlowKind.LOCAL_ASSIGN || kind == FlowKind.FIELD_LOAD || kind == FlowKind.CALL_LOAD){
                        return Traversal.StoredInVar;
                    }else if(kind == FlowKind.NEW){
                        return Traversal.Allocation;
                    }
                }else{
                    if(kind == FlowKind.PARAMETER_PASSING){
                        return Traversal.Finish;
                    }
                }
            }
            case ThisAlias -> {

                if(isForward){
                    if(kind == FlowKind.LOCAL_ASSIGN){
                        return Traversal.ThisAlias;
                    }else if(kind == FlowKind.FIELD_LOAD){
                        if(fieldMatch){
                            return Traversal.DirectVar;
                        }
                    }
                }else{
                    if(kind == FlowKind.FIELD_STORE){
                        if(fieldMatch) return Traversal.StoredInVar;
                    }
                }
            }
        }
        return Traversal.Undef;
    }


    private enum Traversal {
        DirectVar, StoredInVar, Allocation, ThisAlias, THIS, Finish, Undef
    }

}
