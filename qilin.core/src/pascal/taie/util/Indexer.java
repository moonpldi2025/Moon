package pascal.taie.util;

public interface Indexer<E> {
    int getIndex(E o);
    E getObject(int index);
}
