package qilin.pta.toolkits.moon;

import pascal.taie.collection.Sets;
import qilin.core.pag.AllocNode;
import qilin.pta.toolkits.common.OAG;
import qilin.pta.toolkits.moon.BoxTraversal.MatchObjTraversal;
import qilin.pta.toolkits.moon.ContextMatch.ResultOfVarTrace;
import qilin.pta.toolkits.moon.Graph.FieldPointsToGraph;
import qilin.pta.toolkits.moon.Graph.Graph;
import qilin.pta.toolkits.moon.Graph.SimpleGraph;
import soot.ArrayType;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class MatchObjCollector {
    private MatchCaseRecorder matchCaseRecorder;

    /*
     * 1. innerContainers: the container objects that directly contain other objects.
     */
    private Set<AllocNode> innerContainers = Sets.newSet();
    /*
     * 2. wrapperContainers: the container objects that contain other container objects in field, with which share same allocators. (typically are array objects)
     */
    private Set<AllocNode> wrapperContainers = Sets.newSet();
    /*
     * 3. allocatorContainers: the container objects that primarily provide parameters of method call.
     */
    private Set<AllocNode> allocatorContainers = Sets.newSet();

    private final Set<AllocNode> nonInnerContainers = Sets.newSet();


    private final int maxMatchLayer;
    private final FieldPointsToGraph fieldPointsToGraph;
    protected final OAG objectAllocationGraphWithArr;
    protected final Set<AllocNode> preHSObjs;
    private final MatchObjTraversal matchObjTraversal;
    public MatchObjCollector(Set<AllocNode> preHSObjs, OAG objectAllocationGraphWithArr,FieldPointsToGraph fieldPointsToGraph, int maxMatchLayer, MatchObjTraversal matchObjTraversal) {
        this.maxMatchLayer = maxMatchLayer;
        this.fieldPointsToGraph = fieldPointsToGraph;
        this.objectAllocationGraphWithArr = objectAllocationGraphWithArr;
        this.preHSObjs = preHSObjs;
        this.matchObjTraversal = matchObjTraversal;

    }

    public void setMatchCaseRecorder(MatchCaseRecorder matchCaseRecorder) {
        this.matchCaseRecorder = matchCaseRecorder;
    }

    public Set<AllocNode> getMatchedObjs() {

        System.out.println("#WrapContainer: " + (MoonConfig.enableWrapperContainer ? "Enabled" : "Disabled"));
        System.out.println("#AllocatorContainer: " + (MoonConfig.enableAllocatorContainer ? "Enabled" : "Disabled"));
        utilizePreHSObjs();

        Graph<AllocNode> containerGraph = buildContainerGraph();
        innerContainers = retainBoxCanBeSeperated(innerContainers, "#Inner");

        if(MoonConfig.enableWrapperContainer){
            collectWrapperContainersForContainee(containerGraph, innerContainers);
            wrapperContainers = retainBoxCanBeSeperated(wrapperContainers, "#WrapperOfInner");
        }

        if (maxMatchLayer == 1) {
        } else if (maxMatchLayer == 2) {
            if(MoonConfig.enableAllocatorContainer) {
                collectAllocatorContainersFor3obj();
                allocatorContainers = retainBoxCanBeSeperated(allocatorContainers, "#Allocator");
                collectWrapperContainersForContainee(containerGraph, allocatorContainers);
                wrapperContainers = retainBoxCanBeSeperated(wrapperContainers, "#WrapperOfAllocator");
            }
        } else {
            Assertions.panic("maxMatchLayer > 2 not supported yet.");
        }
        Set<AllocNode> ret = Sets.newSet();
        ret.addAll(innerContainers);
        ret.addAll(wrapperContainers);
        ret.addAll(allocatorContainers);

        int oldSize, newSize;
        while (true){
            oldSize = ret.size();
            deleteRedundentObjs(ret, containerGraph);
            newSize = ret.size();
            if (oldSize == newSize) break;
        }


        System.out.println("#Unconditional: " + innerContainers.size());
        System.out.println("#Heap-Conditional: " + wrapperContainers.size());
        System.out.println("#Field-Conditional: " + allocatorContainers.size());
        System.out.println("#Total Precision-Relevant objs: " + ret.size());



        return ret;
    }

    private void utilizePreHSObjs() {
        retainAllInPreHSObjs(innerContainers);
        retainAllInPreHSObjs(nonInnerContainers);
        // remove innerContainers from nonInnerContainers
        nonInnerContainers.removeAll(innerContainers);
    }
    private void retainAllInPreHSObjs(Set<AllocNode> objs) {
        if (preHSObjs != null && ! preHSObjs.isEmpty()) {
            objs.retainAll(preHSObjs);
        }
    }


    private void collectAllocatorContainersFor3obj() {
        for (AllocNode innerContainer : innerContainers) {
            for (ResultOfVarTrace resultOfVarTrace : matchCaseRecorder.objToMatchRet.get(innerContainer)) {
                Set<AllocNode> firstLayerCtxObjs = Sets.newSet(resultOfVarTrace.getMatchedCtxObjsOfParam(1));
                Set<AllocNode> secondLayerCtxObjs = Sets.newSet(resultOfVarTrace.getMatchedCtxObjsOfParam(2));
                if (firstLayerCtxObjs.isEmpty() || secondLayerCtxObjs.isEmpty()) {
                    continue;
                }
                for (AllocNode firstLayerCtxObj : firstLayerCtxObjs) {
                    Set<AllocNode> allocatorsOfFirstLayerCtxObjs = objectAllocationGraphWithArr.getPredsOf(firstLayerCtxObj);
                    if ((firstLayerCtxObj.getMethod() != null && firstLayerCtxObj.getMethod().isStatic()) || Sets.haveOverlap(allocatorsOfFirstLayerCtxObjs, secondLayerCtxObjs)) {
                        allocatorContainers.add(firstLayerCtxObj);
                    }
                }
            }
        }
    }

    private Graph<AllocNode> buildContainerGraph() {
        SimpleGraph<AllocNode> containerGraph = new SimpleGraph<>();
        for (AllocNode nonInnerContainerObj : nonInnerContainers) {
            for (ResultOfVarTrace resultOfVarTrace : matchCaseRecorder.objToMatchRet.get(nonInnerContainerObj)) {
                for (int layer = 0; layer < resultOfVarTrace.getRecordSize(); layer++) {
                    if (layer > maxMatchLayer) break;
                    Set<AllocNode> newlyAllocObjs = resultOfVarTrace.getNewlyAllocObjs(layer);
                    for (AllocNode newlyAllocObj : newlyAllocObjs) {
                        if (newlyAllocObj.equals(nonInnerContainerObj)) continue;
                        Set<AllocNode> allocatorOfNewlyAlloc = objectAllocationGraphWithArr.getPredsOf(newlyAllocObj);
                        Set<AllocNode> allocatorOfNonInner = objectAllocationGraphWithArr.getPredsOf(nonInnerContainerObj);
                        if (Sets.haveOverlap(allocatorOfNonInner, allocatorOfNewlyAlloc)
                                &&
                                !allocatorOfNewlyAlloc.contains(nonInnerContainerObj)
                        ) {
                            containerGraph.addEdge(newlyAllocObj, nonInnerContainerObj);
                        }
                    }
                }
            }
        }
        return containerGraph;
    }


    private void collectWrapperContainersForContainee(Graph<AllocNode> containerGraph, Set<AllocNode> containees) {
        Set<AllocNode> allocNodes = Sets.newSet(containerGraph.getNodes());
        allocNodes.retainAll(containees);
        Queue<AllocNode> queue = new ArrayDeque<>(allocNodes);
        int size = queue.size();
        Queue<Integer> depthQueue = new ArrayDeque<>(Collections.nCopies(size, 0));
        Set<AllocNode> visited = Sets.newSet();
        while (!queue.isEmpty()) {
            AllocNode current = queue.remove();
            int depth = depthQueue.element();
            if (!visited.contains(current)) {
                visited.add(current);
                if (depth < maxMatchLayer) {
                    // Push unvisited successors onto the queue
                    for (AllocNode neighbor : containerGraph.getSuccsOf(current)) {
                        if (!visited.contains(neighbor) && !containees.contains(neighbor) && !wrapperContainers.contains(neighbor)) {
                            queue.add(neighbor);
                            wrapperContainers.add(neighbor);
                            depthQueue.add(depth + 1);
                        }
                    }
                }
            }
        }
    }



    private Set<AllocNode> retainBoxCanBeSeperated(Set<AllocNode> objs, String heapKind){
        objs = objs.parallelStream().filter(matchObjTraversal::canBeSeperated).collect(Collectors.toSet());
        return objs;
    }

    private void deleteRedundentObjs(Set<AllocNode> objs, Graph<AllocNode> containerGraph) {
        Set<AllocNode> toRemoved = Sets.newSet();
        for (AllocNode matchedObj : objs) {
            if (matchedObj.getType() instanceof ArrayType) {
                Set<AllocNode> pts = fieldPointsToGraph.getFieldPointsTo(matchedObj);
                if (pts.size() <= 1 && pts.stream().noneMatch(objs::contains)) {
                    toRemoved.add(matchedObj);
                }
            } else {
                // class obj.
                boolean isRemoved = fieldPointsToGraph.getAllFieldsOf(matchedObj).stream().allMatch(field -> {
                    Set<AllocNode> pts = fieldPointsToGraph.getFieldPointsTo(matchedObj, field);
                    return pts.size() <= 1 && pts.stream().noneMatch(objs::contains);
                });
                if (isRemoved) {
                    toRemoved.add(matchedObj);
                    toRemoved.addAll(containerGraph.getSuccsOf(matchedObj));
                }
            }
        }
        objs.removeAll(toRemoved);
        innerContainers.removeAll(toRemoved);
        wrapperContainers.removeAll(toRemoved);
        allocatorContainers.removeAll(toRemoved);
    }


    public void addToInnerContainers(AllocNode obj) {
        innerContainers.add(obj);
    }
    public void addToNonInnerContainers(AllocNode obj) {
        nonInnerContainers.add(obj);
    }



}
