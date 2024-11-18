package qilin.pta.toolkits.moon.ContextMatch.ObjFilters.FiltersBeforeMatch;


import pascal.taie.collection.Sets;
import qilin.core.pag.AllocNode;
import qilin.pta.toolkits.common.OAG;
import qilin.pta.toolkits.moon.ContextMatch.ObjFilters.ObjFilter;

import java.util.Set;
import java.util.stream.Collectors;

public class MultiCtxFilter implements ObjFilter {

    private final int maxMatchLayer;
    private final OAG objectAllocationGraphWithArr;
    private final Set<AllocNode> objsWithoutEnoughCtx = Sets.newSet();
    public MultiCtxFilter(int maxMatchLayer, OAG objectAllocationGraphWithArr) {
        this.maxMatchLayer = maxMatchLayer;
        this.objectAllocationGraphWithArr = objectAllocationGraphWithArr;
    }
    @Override
    public boolean filterBeforeMatch(AllocNode obj) {
        Set<AllocNode> crtObjs = Sets.newSet();
        crtObjs.add(obj);
        for (int i = 0; i < this.maxMatchLayer; i++) {
            Set<AllocNode> preds = crtObjs.stream().map(objectAllocationGraphWithArr::getPredsOf).flatMap(Set::stream).collect(Collectors.toSet());
            if(preds.size() > 1) return true;
            crtObjs = preds;
        }
        objsWithoutEnoughCtx.add(obj);
        return false;
    }
}
