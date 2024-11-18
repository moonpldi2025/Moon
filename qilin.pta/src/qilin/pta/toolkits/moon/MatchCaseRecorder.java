package qilin.pta.toolkits.moon;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import pascal.taie.collection.Sets;
import qilin.core.pag.AllocNode;
import qilin.core.pag.LocalVarNode;
import qilin.pta.toolkits.moon.ContextMatch.ResultOfVarTrace;
import qilin.pta.toolkits.moon.ContextMatch.VarStoredTracer;
import soot.jimple.spark.pag.SparkField;

import java.util.*;

public class MatchCaseRecorder {

    protected final VarStoredTracer varStoredTracer;
    public final MultiMap<AllocNode, ResultOfVarTrace> objToMatchRet = Maps.newMultiMap();
    protected final int maxMatchLayer;
    private final MatchObjCollector matchObjCollector;
    private final Set<AllocNode> metParamOfAllocatedMethod = Sets.newSet();
    public MatchCaseRecorder(VarStoredTracer varStoredTracer,  int maxMatchLayer, MatchObjCollector matchObjCollector) {
        this.varStoredTracer = varStoredTracer;
        this.maxMatchLayer = maxMatchLayer;
        this.matchObjCollector = matchObjCollector;
    }

    public void matchedAndRecord(AllocNode obj, LocalVarNode varStored, int checkLayer, int fullMatchLayer, SparkField storedField) {
        ResultOfVarTrace resultOfVarTrace = varStoredTracer.findSourceOfVarStoredIn(obj, varStored, fullMatchLayer, storedField);
            if (resultOfVarTrace.hasCtxObjsOnLayerOf(checkLayer)) {
                matchObjCollector.addToInnerContainers(obj);
                saveMatchRet(obj, checkLayer, resultOfVarTrace);
            } else if (resultOfVarTrace.hasNewlyAllocObjs()) {
                matchObjCollector.addToNonInnerContainers(obj);
                saveMatchRet(obj, checkLayer, resultOfVarTrace);
            }

        if(resultOfVarTrace.isMetParamOfAllocatedMethod()){
            this.metParamOfAllocatedMethod.add(obj);
        }
    }

    public boolean isMetParamOfAllocatedMethod(AllocNode obj){
        return this.metParamOfAllocatedMethod.contains(obj);
    }

    private void saveMatchRet(AllocNode obj, int checkLayer, ResultOfVarTrace resultOfVarTrace) {
        objToMatchRet.put(obj, resultOfVarTrace);
    }




}
