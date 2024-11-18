package qilin.pta.toolkits.moon.ContextMatch;



import pascal.taie.collection.*;
import qilin.core.PTA;
import qilin.core.builder.MethodCallDetail;
import qilin.core.pag.AllocNode;
import qilin.core.pag.LocalVarNode;
import qilin.pta.toolkits.common.OAG;
import qilin.pta.toolkits.moon.*;
import qilin.pta.toolkits.moon.Graph.FieldPointsToGraph;
import qilin.pta.toolkits.moon.BoxTraversal.MatchObjTraversal;
import qilin.pta.toolkits.moon.Graph.VFG;
import soot.*;
import soot.jimple.spark.pag.SparkField;

import java.util.Set;

public class FieldMatcher {

    protected final VFG VFGForField;
    protected final int maxCtxLayer;
    private final MethodCallDetail methodCallDetail;
    protected final OAG objectAllocationGraphWithArr;

    protected final MultiMap<AllocNode, SootMethod> objToInvokedMethods;

    private final MultiMap<AllocNode, Object> objToLoadedField = Maps.newConcurrentMultiMap();
    private final FieldRecorder fieldRecorder;
    private final MatchCaseRecorder matchCaseRecorder;
    private final MatchObjCollector matchObjCollector;
    private final FieldFlowRecorder fieldReachabilityRecorder;
    protected final PTA ciResult;
    protected final PtrSetCache ptrSetCache;
    public FieldMatcher(PTA ciResult, int maxCtxLayer, MoonGraphBuilder graphBuilder, Set<AllocNode> preHsObjs, FieldFlowRecorder fieldReachabilityRecorder, PtrSetCache ptrSetCache){
        this.ciResult = ciResult;
        this.fieldReachabilityRecorder = fieldReachabilityRecorder;
        this.VFGForField = graphBuilder.getVFGForField();
        this.objToInvokedMethods = graphBuilder.getObjToInvokedMethods();
        this.objectAllocationGraphWithArr = graphBuilder.getObjectAllocationGraph();
        this.fieldRecorder = graphBuilder.getFieldRecorder();
        this.methodCallDetail = MethodCallDetail.v();
        this.maxCtxLayer = maxCtxLayer;
        this.ptrSetCache = ptrSetCache;
        FieldPointsToGraph fieldPointsToGraph = new FieldPointsToGraph(ciResult, ptrSetCache);
        VarStoredTracer varStoredTracer = new VarStoredTracer(this, fieldPointsToGraph, graphBuilder.getAllocatedVars());
        MatchObjTraversal matchObjTraversal = new MatchObjTraversal(ciResult, graphBuilder);
        matchObjCollector = new MatchObjCollector(preHsObjs, objectAllocationGraphWithArr, fieldPointsToGraph, maxCtxLayer, matchObjTraversal);
        matchCaseRecorder = new MatchCaseRecorder(varStoredTracer, maxCtxLayer, matchObjCollector);
        matchObjCollector.setMatchCaseRecorder(matchCaseRecorder);
        matchObjTraversal.setMatchCaseRecorder(matchCaseRecorder);
    }



    public Set<AllocNode> getMatchedObjs(){
        return matchObjCollector.getMatchedObjs();
    }

    public void recordLoad(AllocNode obj, LocalVarNode var){
        for (SparkField loadedField : fieldRecorder.getLoadedFields(var)) {
            for (LocalVarNode loadedToVar : fieldRecorder.getLoadedToVars(var, loadedField)) {
                if(!methodCallDetail.isReceiver(loadedToVar)){
                    if(!VFGForField.getSuccsOf(loadedToVar).isEmpty()){
                        objToLoadedField.put(obj, loadedField);
                    }
                }else{
                    objToLoadedField.put(obj, loadedField);
                }
            }
        }
    }

    public void matchStore(AllocNode obj, LocalVarNode var, boolean isWithoutCtxObj){
        if(notInAllocatorMethod(obj, var) || var.getMethod().isStatic()){
            return;
        }
        for (SparkField storedField : fieldRecorder.getStoredFields(var)) {
            if(!isWithoutCtxObj){
                for (LocalVarNode storedFromVar : fieldRecorder.getStoredFromVars(var, storedField)) {
                    if(storedFromVar.getType() instanceof NullType) continue;
                    if(isIgnoredField(obj, storedField)) continue;
                    if(!fieldReachabilityRecorder.isConnceredField(obj, storedField)) continue;
                    matchStoredVar(storedFromVar, obj, storedField);
                }
            }else{
                throw new RuntimeException("should not reach here");

            }
        }
    }




    private void matchStoredVar(LocalVarNode varStored, AllocNode obj, SparkField storedField){
        int checkLayer = 1;
        int fullMatchLayer = this.maxCtxLayer;
        if(!(obj.getType() instanceof ArrayType) &&  onlyOneDirectAllocator(obj) ) {
            if(fullMatchLayer == 1) throw new RuntimeException("fullMatchLayer = 1 here.");
            checkLayer += 1;
        }
        matchCaseRecorder.matchedAndRecord(obj, varStored, checkLayer, fullMatchLayer, storedField);
    }


    private boolean notInAllocatorMethod(AllocNode obj, LocalVarNode var){
        SootMethod inMethod = var.getMethod();
        if(!objToInvokedMethods.containsKey(obj) || objToInvokedMethods.get(obj).contains(inMethod)) return false;
        Set<AllocNode> allocators = objectAllocationGraphWithArr.getPredsOf(obj);
        if(allocators.stream().anyMatch(o -> objToInvokedMethods.containsKey(o) && objToInvokedMethods.get(o).contains(inMethod))) return false;
        return true;
    }








    private boolean onlyOneDirectAllocator(AllocNode obj){
        return maxCtxLayer > 1 && objectAllocationGraphWithArr.getPredsOf(obj).size() == 1;
    }
    private boolean isIgnoredField(AllocNode obj, SparkField field){
        boolean isRefType;

        if(obj.getType() instanceof ArrayType arrayType) {
            isRefType = arrayType.baseType instanceof RefType;
        }else{
            Type type = field.getType();
            if(type instanceof PrimType) isRefType = false;
            else if(type instanceof RefType) isRefType = true;
            else if(type instanceof ArrayType arrayType){
                isRefType = arrayType.baseType instanceof RefType;
            }else {
                throw new RuntimeException("Unexpected type: " + type);
            }
        }
        return !isRefType;
    }
}
