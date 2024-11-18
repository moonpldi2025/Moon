package qilin.pta.toolkits.moon.ContextMatch;


import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import pascal.taie.collection.Sets;
import pascal.taie.util.Timer;
import qilin.core.PTA;
import qilin.core.pag.*;
import qilin.pta.toolkits.common.OAG;
import qilin.pta.toolkits.moon.Graph.VFG;
import qilin.pta.toolkits.moon.PtrSetCache;
import qilin.pta.toolkits.moon.MoonGraphBuilder;
import qilin.pta.toolkits.moon.FieldRecorder;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ContextMatcherNew {
    protected final MultiMap<AllocNode, LocalVarNode> objToContainerVarMap = Maps.newConcurrentMultiMap();
    protected final PTA ptaResult;
    protected final OAG objectAllocationGraphWithArr;
    protected final int maxMatchLayer;
    protected final VFG VFGForField;
    private final Map<String, Object> options = Maps.newMap();
    private final FieldRecorder fieldRecorder;
    protected final FieldMatcher fieldMatcher;
    private final MatchFilter matchFilter;
    private final PAG pag;
    protected final PtrSetCache ptrSetCache;

    public ContextMatcherNew(int maxMatchLayer, PTA ptaResult) {
        this.ptaResult = ptaResult;
        ptrSetCache = new PtrSetCache(ptaResult);
        this.pag = ptaResult.getPag();
        this.maxMatchLayer = maxMatchLayer;

        MoonGraphBuilder graphBuilder = new MoonGraphBuilder(ptaResult, ptrSetCache);
        objectAllocationGraphWithArr = graphBuilder.getObjectAllocationGraph();
        VFGForField = graphBuilder.getVFGForField();
        fieldRecorder = graphBuilder.getFieldRecorder();


        Set<AllocNode> preHsObjs = Sets.newSet(graphBuilder.getContainers());


        collectContainerObj(preHsObjs);
        fieldMatcher = new FieldMatcher(ptaResult, maxMatchLayer, graphBuilder, preHsObjs, graphBuilder.getFieldFlowRecorder(), ptrSetCache);
        matchFilter = new MatchFilter(this);

    }

    private void collectContainerObj(Set<AllocNode> preHsObjs) {
        Set<AllocNode> containerObj = VFGForField.getContainerVars().parallelStream()
                .map(ptrSetCache::ptsOf)
                .flatMap(Collection::stream)
                .filter(allocNode -> {
                    if (!preHsObjs.contains(allocNode)) return false;
                    if (allocNode instanceof ContextAllocNode) throw new RuntimeException("ContextAllocNode detected!");
                    return !(allocNode instanceof ConstantNode) && allocNode.getMethod() != null;
                })
                .collect(Collectors.toSet());
        VFGForField.getContainerVars().parallelStream().forEach(localVarNode -> {
            ptrSetCache.ptsOf(localVarNode).forEach(allocNode -> {
                if (containerObj.contains(allocNode)) {
                    objToContainerVarMap.put(allocNode, localVarNode);
                }
            });
        });
    }

    public Set<AllocNode> match() {
        Set<AllocNode> concernObjs = matchFilter.filterBeforeMatch(objToContainerVarMap.keySet());

        System.out.println(objToContainerVarMap.keySet().size() + " objects / concerned size:" + concernObjs.size());
        Timer.runAndCount(() -> matchConcernedObjs(concernObjs), "#Seperable Path Identification");
        AtomicReference<Set<AllocNode>> matchedObjs = new AtomicReference<>(Sets.newSet());
        Timer.runAndCount(() -> matchedObjs.set(fieldMatcher.getMatchedObjs()), "Precision-Relevant Heaps Collection");
        return matchedObjs.get();
    }


    private void matchConcernedObjs(Set<AllocNode> concernObjs) {
        int counter = 0;

        recordFieldLoadForConcernObjs(concernObjs);

        for (AllocNode concernObj : concernObjs) {
            counter += 1;
            if (counter % 1000 == 0)
                System.out.println(counter + "/" + concernObjs.size() + "/" + String.format("%.2f", counter * 1.0 / concernObjs.size() * 100) + "%");
            matchCtxOfField(concernObj);
        }
    }

    private void matchCtxOfField(AllocNode obj) {
        Iterator<LocalVarNode> storedToVarItr = objToContainerVarMap.get(obj).parallelStream()
                .filter(var -> fieldRecorder.hasStore(var, obj))
                .iterator();
        while (storedToVarItr.hasNext()) {
            LocalVarNode varStoredTo = storedToVarItr.next();
            fieldMatcher.matchStore(obj, varStoredTo, false);
        }
    }


    private void recordFieldLoadForConcernObjs(Set<AllocNode> concernObjs) {
        concernObjs.parallelStream().forEach(obj -> {
            objToContainerVarMap.get(obj).stream()
                    .filter(var -> fieldRecorder.hasLoad(var, obj)).forEach(varLoaded -> {
                        fieldMatcher.recordLoad(obj, varLoaded);
                    });
        });
    }


}
