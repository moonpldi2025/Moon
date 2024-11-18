package qilin.pta.toolkits.moon.BoxTraversal;

import qilin.core.pag.Node;

import java.util.*;

public class ForwardTrace {

    private final Node node;
    private final Traversal state;

    public ForwardTrace(Node node, Traversal state) {
        this.node = node;
        this.state = state;
    }

    public Node getNode() {
        return node;
    }

    public Traversal getState() {
        return state;
    }
    @Override
    public int hashCode() {
        return Objects.hash(node, state);
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof ForwardTrace other){
            if(o == this) return true;
            return this.node.equals(other.node) && this.state.equals(other.state);
        }
        return false;
    }

}
