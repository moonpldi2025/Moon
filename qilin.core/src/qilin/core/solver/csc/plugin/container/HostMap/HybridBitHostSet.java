package qilin.core.solver.csc.plugin.container.HostMap;

import pascal.taie.collection.HybridBitSet;
import pascal.taie.collection.SetEx;
import pascal.taie.util.Indexer;
import qilin.core.solver.csc.plugin.container.Host;

class HybridBitHostSet extends DelegateHostSet {

    public HybridBitHostSet(Indexer<Host> indexer) {
        this(new HybridBitSet<>(indexer, true));
    }

    public HybridBitHostSet(SetEx<Host> set) {
        super(set);
    }

    @Override
    protected HostSet newSet(SetEx<Host> set) {
        return new HybridBitHostSet(set);
    }
}
