package qilin.pta.toolkits.moon.Graph;


import pascal.taie.collection.Maps;
import pascal.taie.collection.TwoKeyMultiMap;
import qilin.core.PTA;
import qilin.core.pag.AllocNode;
import qilin.core.pag.ContextAllocNode;
import qilin.core.pag.ContextField;
import qilin.pta.toolkits.moon.PtrSetCache;
import soot.ArrayType;
import soot.RefType;
import soot.Type;
import soot.jimple.spark.pag.SparkField;

import java.util.Set;

public class FieldPointsToGraph {
    
    private final SimpleGraph<AllocNode> fieldPointsToGraph = new SimpleGraph<>();
    private final TwoKeyMultiMap<AllocNode, AllocNode, SparkField> objToStoredToField = Maps.newTwoKeyMultiMap();
    private final TwoKeyMultiMap<AllocNode, SparkField, AllocNode> objToFieldToStored = Maps.newTwoKeyMultiMap();
    private final PTA pta;
    private final PtrSetCache ptrSetCache;
    public FieldPointsToGraph(PTA pta, PtrSetCache ptrSetCache) {
        this.pta = pta;
        this.ptrSetCache = ptrSetCache;
        init();
    }
    public void addFieldPointsToGraph(AllocNode baseObj, SparkField field, AllocNode obj) {
        if(baseObj instanceof  ContextAllocNode contextAllocNode){
            baseObj = contextAllocNode.base();
        }
        if(obj instanceof  ContextAllocNode contextAllocNode){
            obj = contextAllocNode.base();
        }
        fieldPointsToGraph.addEdge(baseObj, obj);
        objToStoredToField.put(baseObj, obj, field);
        objToFieldToStored.put(baseObj, field, obj);

    }
    public boolean hasFieldPointsTo(AllocNode baseObj, AllocNode obj) {
        return fieldPointsToGraph.hasEdge(baseObj, obj);
    }

    public Set<AllocNode> getFieldPointsTo(AllocNode baseObj, SparkField field) {
        return objToFieldToStored.get(baseObj, field);
    }
    public Set<SparkField> getAllFieldsOf(AllocNode baseObj){
        return objToFieldToStored.get(baseObj).keySet();
    }

    public Set<AllocNode> getFieldPointsTo(AllocNode baseObj) {
        return fieldPointsToGraph.getSuccsOf(baseObj);
    }

    public Set<AllocNode> getFieldPointsFrom(AllocNode obj) {
        return fieldPointsToGraph.getPredsOf(obj);
    }

    private void init(){
        for (ContextField contextField : pta.getPag().getContextFields()) {
            Set<AllocNode> pts = ptrSetCache.ptsOf(contextField);
            if(isIgnored(contextField.getField().getType()) || pts.isEmpty()) continue;
            AllocNode baseObj = contextField.getBase();
            if(baseObj instanceof ContextAllocNode contextAllocNode){
                baseObj = contextAllocNode.base();
            }
            if (baseObj.getMethod() == null) {
                continue;
            }
            AllocNode finalBaseObj = baseObj;
            SparkField field = contextField.getField();
            pts.forEach(o -> addFieldPointsToGraph(finalBaseObj, field, o));

        }
    }
    private boolean isIgnored(Type type){
        if(type instanceof ArrayType arrayType) {
            type = arrayType.baseType;
        }
        return !(type instanceof RefType);
    }



}



