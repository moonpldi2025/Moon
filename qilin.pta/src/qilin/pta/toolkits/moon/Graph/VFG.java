package qilin.pta.toolkits.moon.Graph;

import qilin.core.pag.*;
import pascal.taie.collection.*;
import qilin.pta.toolkits.moon.Assertions;
import soot.SootMethod;
import soot.jimple.spark.pag.SparkField;

import java.util.*;

public class VFG {

    private final Set<LocalVarNode> thisVars = Sets.newConcurrentSet();

    private final MultiMap<Node, FlowEdge> outEdges = Maps.newConcurrentMultiMap();
    private final MultiMap<Node, FlowEdge> inEdges = Maps.newConcurrentMultiMap();


    private final Set<LocalVarNode> containerVars = Sets.newConcurrentSet();

//    private final Set<SootMethod> inlineCallees = Sets.newConcurrentSet();
    private final Set<SootMethod> inlineCallers = Sets.newConcurrentSet();


    public void addFieldFlowEdge(FlowKind flowKind, Node src, Node tgt, SparkField field) {
        if(flowKind == FlowKind.FIELD_LOAD){
            containerVars.add((LocalVarNode) src);
        }else if(flowKind == FlowKind.FIELD_STORE){
            containerVars.add((LocalVarNode) tgt);
        }else{
            Assertions.panic("Unexpected flow kind: " + flowKind);
        }
        FieldEdge edge = new FieldEdge(src, tgt, flowKind, field);
        outEdges.put(src, edge);
        inEdges.put(tgt, edge);
    }

    public void addSimpleFlowEdge(FlowKind flowKind, Node src, Node tgt) {
        if(flowKind == FlowKind.CALL_LOAD) {
            containerVars.add((LocalVarNode) src);
        } else if (flowKind == FlowKind.CALL_STORE) {
            containerVars.add((LocalVarNode) tgt);
        }
        FlowEdge edge = new FlowEdge(src, tgt, flowKind);
        outEdges.put(src, edge);
        inEdges.put(tgt, edge);
    }

    public void recordThisVar(VarNode thisVar){
        thisVars.add((LocalVarNode) thisVar);
    }

    public Set<LocalVarNode> getThisVars() {
        return thisVars;
    }

    public Set<FlowEdge> getPredsOf(Node node){
        return inEdges.get(node);
    }

    public Set<FlowEdge> getSuccsOf(Node node){
        return outEdges.get(node);
    }

    public Set<Node> getNodes(){
        Set<Node> ret = Sets.newSet();
        ret.addAll(inEdges.keySet());
        ret.addAll(outEdges.keySet());
        return ret;
    }

    public Set<LocalVarNode> getContainerVars() {
        return containerVars;
    }

    public void recordInlineMethod(SootMethod callee, SootMethod caller){
//        inlineCallees.add(callee);
        inlineCallers.add(caller);
    }

    public Set<SootMethod> getInlineCallers() {
        return inlineCallers;
    }
}

