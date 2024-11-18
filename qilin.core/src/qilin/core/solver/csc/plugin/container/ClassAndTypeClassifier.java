package qilin.core.solver.csc.plugin.container;


import pascal.taie.collection.Maps;
import qilin.core.PTAScene;
import soot.*;

import java.util.List;
import java.util.Map;

public class ClassAndTypeClassifier {


    private static final SootClass mapClass =  getSootClass("java.util.Map");
    private static final SootClass collectionClass = getSootClass("java.util.Collection");
    private static final SootClass mapEntryClass = getSootClass("java.util.Map$Entry");

    private static final SootClass iteratorClass = getSootClass("java.util.Iterator");

    private static final SootClass enumerationClass = getSootClass("java.util.Enumeration");

    private static final SootClass hashtableClass = getSootClass("java.util.Hashtable");
    private static final RefType hashtableType = getClassType("java.util.Hashtable");

    private static final SootClass vectorClass = getSootClass("java.util.Vector");
    private static final RefType vectorType = getClassType("java.util.Vector");

    private static final SootClass abstractList = getSootClass("java.util.AbstractList");

    public enum containerType {
        MAP, COLLECTION, OTHER
    }

    private static SootClass getSootClass(String clz){
        return PTAScene.v().getSootClass(clz);
    }
    
    private static RefType getClassType(String clz){
        return PTAScene.v().getSootClass(clz).getType();
    }



    private static boolean isSubclass(SootClass superClz, SootClass child){
        if(superClz.isInterface()){
            return ContainerConfig.getAllSubClasses(superClz).contains(child);
//            return hierarchy.getImplementersOf(superClz).contains(child);
//            return hierarchy.isInterfaceSuperinterfaceOf(child, superClz);
        }else
            return ContainerConfig.getAllSubClasses(superClz).contains(child);
//            return hierarchy.getSubclassesOf(superClz).contains(child);
//            return hierarchy.isClassSubclassOf(child, superClz);
    }
    private static boolean isSubType(Type superType, Type child){
        return Scene.v().getOrMakeFastHierarchy().canStoreType(child, superType);
    }
    public static containerType ClassificationOf(RefType type) {
        SootClass clz = getSootClass(type.getClassName());
        if (clz == null)
            return containerType.OTHER;
        if (isSubclass(mapClass, clz))
            return containerType.MAP;
        if (isSubclass(collectionClass, clz))
            return containerType.COLLECTION;
        return containerType.OTHER;
    }

    public static boolean isIteratorClass(SootClass iterator) {
        return isSubclass(iteratorClass, iterator);
    }

    public static boolean isEnumerationClass(SootClass enumeration) {
        return isSubclass(enumerationClass, enumeration) && !enumeration.isApplicationClass();
    }

    public static boolean isAbstractListClass(SootClass clz) {
        return isSubclass(abstractList, clz);
    }

    public static boolean isMapEntryClass(SootClass entry) {
        return isSubclass(mapEntryClass, entry) && !entry.isApplicationClass();
    }

    public static boolean isHashtableClass(SootClass hashtable) {
        return isSubclass(hashtableClass, hashtable) && !hashtable.isApplicationClass();
    }

    public static boolean isVectorClass(SootClass vector) {
        return isSubclass(vectorClass, vector) && !vector.isApplicationClass();
    }

    public static boolean isVectorType(Type vector) {
        return isSubType(vectorType, vector);
    }

    public static boolean isHashtableType(Type hashtable) {
        return isSubType(hashtableType, hashtable);
    }


}
