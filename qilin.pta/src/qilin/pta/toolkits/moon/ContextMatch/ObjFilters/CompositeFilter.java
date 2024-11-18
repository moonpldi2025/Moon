package qilin.pta.toolkits.moon.ContextMatch.ObjFilters;

import qilin.core.pag.AllocNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompositeFilter implements ObjFilter{
    private final List<ObjFilter> beforeFilters = new ArrayList<>();
    private final List<ObjFilter> afterFilters = new ArrayList<>();
    public void addBeforeFilter(ObjFilter filter){
        beforeFilters.add(filter);
    }

    public void addAfterModifier(ObjFilter filter){
        afterFilters.add(filter);
    }


    @Override
    public boolean filterBeforeMatch(AllocNode obj) {
        return beforeFilters.stream().allMatch(objFilter -> objFilter.filterBeforeMatch(obj));
    }
}
