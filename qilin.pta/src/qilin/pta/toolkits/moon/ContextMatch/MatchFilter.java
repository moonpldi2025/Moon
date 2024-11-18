package qilin.pta.toolkits.moon.ContextMatch;


import qilin.core.pag.AllocNode;
import qilin.pta.toolkits.moon.ContextMatch.ObjFilters.CompositeFilter;
import qilin.pta.toolkits.moon.ContextMatch.ObjFilters.FiltersBeforeMatch.MultiCtxFilter;

import java.util.Set;
import java.util.stream.Collectors;

public class MatchFilter {
    private final CompositeFilter filter = new CompositeFilter();

    private final ContextMatcherNew matcher;
    private Set<AllocNode> filteredBeforeMatch;
    public MatchFilter(ContextMatcherNew matcher){
        this.matcher = matcher;
        addFilterBeforeMatch();
        addModifierAfterMatch();
    }


    private void addFilterBeforeMatch(){
        MultiCtxFilter multiCtxFilter = new MultiCtxFilter(matcher.maxMatchLayer, matcher.objectAllocationGraphWithArr);
        filter.addBeforeFilter(multiCtxFilter);
    }

    private void addModifierAfterMatch(){
    }

    public Set<AllocNode> filterBeforeMatch(Set<AllocNode> objs){
        filteredBeforeMatch = objs.stream().filter(filter::filterBeforeMatch)
                .collect(Collectors.toSet());
        return filteredBeforeMatch;
    }
}

