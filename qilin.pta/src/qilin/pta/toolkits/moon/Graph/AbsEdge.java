package qilin.pta.toolkits.moon.Graph;

import pascal.taie.collection.Hashes;

public abstract class AbsEdge<Node>{
    private final Node source;
    private final Node target;

    public AbsEdge(Node source, Node target){
        this.source = source;
        this.target = target;
    }
    public Node source(){
        return source;
    }

    public Node target(){
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbsEdge<?> edge = (AbsEdge<?>) o;
        return source().equals(edge.source()) && target().equals(edge.target());
    }

    @Override
    public int hashCode() {
        return Hashes.hash(source(), target());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{" + source() + " -> " + target() + '}';
    }
}
