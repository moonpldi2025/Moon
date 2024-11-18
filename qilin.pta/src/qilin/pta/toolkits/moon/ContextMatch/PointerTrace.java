package qilin.pta.toolkits.moon.ContextMatch;

import pascal.taie.collection.Maps;
import qilin.core.pag.FlowKind;
import qilin.core.pag.LocalVarNode;
import qilin.core.pag.Node;
import soot.SootMethod;

import java.util.*;

public class PointerTrace {
    public Node pointer;
    public int allocatorLevel;
    public int callStackLevel;
    private Stack<SootMethod> staticCallStack;
    private Map<FlowKind, Integer> visitedFlowKinds;
    public boolean needCheckFieldRelationWhenMeetNew = true;
    public PointerTrace(Node pointer, int allocatorLevel, int callStackLevel, Node retPtrOfStaticMethod, PointerTrace predTrace, FlowKind flowKind) {
        this(pointer, allocatorLevel, callStackLevel, predTrace, flowKind);
        if(retPtrOfStaticMethod instanceof LocalVarNode localVarNode){
            if(staticCallStack == null){
                staticCallStack = new Stack<>();
            }
            staticCallStack.push(localVarNode.getMethod());
        }
    }


    public PointerTrace(Node pointer, int allocatorLevel, int callStackLevel, PointerTrace predTrace, FlowKind flowKind) {
        this(pointer, allocatorLevel, callStackLevel);
        if(this.visitedFlowKinds == null){
            visitedFlowKinds = Maps.newMap();
        }
        if(predTrace.visitedFlowKinds != null && !predTrace.visitedFlowKinds.isEmpty()){
            this.visitedFlowKinds.putAll(predTrace.visitedFlowKinds);
        }
        int i = this.visitedFlowKinds.computeIfAbsent(flowKind, __ -> 0);
        i += 1;
        this.visitedFlowKinds.put(flowKind, i);
        if(predTrace.staticCallStack != null && !predTrace.staticCallStack.isEmpty()){
            if(staticCallStack == null){
                staticCallStack = new Stack<>();
            }
            staticCallStack.addAll(predTrace.staticCallStack);
        }

        this.needCheckFieldRelationWhenMeetNew = predTrace.needCheckFieldRelationWhenMeetNew;
    }

    public PointerTrace(Node pointer, int allocatorLevel,int callStackLevel) {
        this.pointer = pointer;
        this.allocatorLevel = allocatorLevel;
        this.callStackLevel = callStackLevel;
    }

    public SootMethod getStaticCallStackTop(){
        if(staticCallStack != null && !staticCallStack.isEmpty()){
            return staticCallStack.peek();
        }
        return null;
    }

    public int getVisitedFlowCounter(FlowKind flowKind){
        if(this.visitedFlowKinds == null) return 0;
        return this.visitedFlowKinds.getOrDefault(flowKind, 0);
    }


    @Override
    public int hashCode() {
        return Objects.hash(pointer, callStackLevel, allocatorLevel);
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof PointerTrace other){
            if(o == this) return true;
            return this.pointer.equals(other.pointer) &&
                    this.allocatorLevel == other.allocatorLevel &&
                    this.callStackLevel == other.callStackLevel;
        }else {
            return false;
        }
    }
}
