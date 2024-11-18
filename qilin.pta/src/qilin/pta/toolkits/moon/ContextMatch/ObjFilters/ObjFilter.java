package qilin.pta.toolkits.moon.ContextMatch.ObjFilters;

import qilin.core.pag.AllocNode;

import java.util.Set;

public interface ObjFilter {
    default boolean filterBeforeMatch(AllocNode obj){
        return true;
    }
    default boolean modifyAfterMatch(Set<AllocNode> matchedObjs){
        return false;
    }

}
