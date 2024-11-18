package qilin.pta.toolkits.moon;

import pascal.taie.collection.Maps;
import pascal.taie.collection.MultiMap;
import pascal.taie.collection.TwoKeyMultiMap;
import qilin.core.pag.AllocNode;
import qilin.core.pag.ArrayElement;
import qilin.core.pag.Field;
import qilin.core.pag.LocalVarNode;
import soot.*;
import soot.jimple.spark.pag.SparkField;

import java.util.Set;
import java.util.stream.Collectors;

public class FieldRecorder {


    private final TwoKeyMultiMap<LocalVarNode, SparkField, LocalVarNode> varToFieldToStoreFromVar = Maps.newConcurrentTwoKeyMultiMap();
    private final TwoKeyMultiMap<LocalVarNode, SparkField, LocalVarNode> varToFieldToLoadedToVar = Maps.newConcurrentTwoKeyMultiMap();

    protected final MultiMap<AllocNode, SparkField> objToFields = Maps.newConcurrentMultiMap();
    protected final MultiMap<Type, SparkField> typeToFields = Maps.newConcurrentMultiMap();

    protected final MultiMap<SparkField, AllocNode> fieldToBaseObjs = Maps.newConcurrentMultiMap();

    private final MultiMap<SparkField, SootMethod> fieldToInMethods = Maps.newConcurrentMultiMap();
    public void putStore(LocalVarNode var, SparkField field, LocalVarNode storeFromVar){
        if(field instanceof ArrayElement && !field.equals(ArrayElement.v())) throw new RuntimeException("ArrayElement should be the same object");
        varToFieldToStoreFromVar.put(var, field, storeFromVar);
        if(!(field instanceof ArrayElement)){
            // skip arrayElement
            fieldToInMethods.put(field, var.getMethod());
        }
    }

    public void putLoad(LocalVarNode var, SparkField field, LocalVarNode loadedToVar){
        if(field instanceof ArrayElement && !field.equals(ArrayElement.v())) throw new RuntimeException("ArrayElement should be the same object");
        varToFieldToLoadedToVar.put(var, field, loadedToVar);
        if(!(field instanceof ArrayElement)){
            // skip arrayElement
            fieldToInMethods.put(field, var.getMethod());
        }
    }
    public boolean hasStore(LocalVarNode var, AllocNode obj){
        return hasUsage(varToFieldToStoreFromVar, var, obj);
    }

    public boolean hasLoad(LocalVarNode var, AllocNode obj){
        return hasUsage(varToFieldToLoadedToVar, var, obj);
    }

    private boolean hasUsage(TwoKeyMultiMap<LocalVarNode, SparkField, LocalVarNode> usageMap, LocalVarNode var, AllocNode obj){
        if(obj.getType() instanceof ArrayType){
            return usageMap.containsKey(var);
        }
        if(!usageMap.containsKey(var)) return false;
        Set<SootField> usageFields = usageMap.get(var).keySet().stream().map(sf -> ((Field) sf).getField()).collect(Collectors.toSet());
        if(usageFields.isEmpty()) throw new RuntimeException("fields usage is empty where it should not be.");
        return !usageFields.isEmpty();
    }


    public Set<LocalVarNode> getStoredFromVars(LocalVarNode var, SparkField field){
        if(field instanceof ArrayElement && !field.equals(ArrayElement.v())) throw new RuntimeException("ArrayElement should be the same object");
        return varToFieldToStoreFromVar.get(var, field);
    }

    public Set<LocalVarNode> arrGetStoredFromVars(LocalVarNode var){
        return getStoredFromVars(var, ArrayElement.v());
    }

    public Set<LocalVarNode> getLoadedToVars(LocalVarNode var, SparkField field){
        if(field instanceof ArrayElement && !field.equals(ArrayElement.v())) throw new RuntimeException("ArrayElement should be the same object");
        return varToFieldToLoadedToVar.get(var, field);
    }

    public Set<SparkField> getLoadedFields(LocalVarNode var){
        return varToFieldToLoadedToVar.get(var).keySet();
    }

    public Set<SparkField> getStoredFields(LocalVarNode var){
        return varToFieldToStoreFromVar.get(var).keySet();
    }


    public Set<SootMethod> getInMethodsOf(SparkField field){
        return fieldToInMethods.get(field);
    }

    public Set<SparkField> allFields(KeyTypeCollector openTypeCollector){
        return objToFields.values().parallelStream().filter(f -> openTypeCollector.isConcernedType(f.getType())).collect(Collectors.toSet());
    }

    public void recordObjToField(AllocNode obj, SparkField field){
        objToFields.put(obj, field);
        typeToFields.put(obj.getType(), field);
        fieldToBaseObjs.put(field, obj);
    }



}
