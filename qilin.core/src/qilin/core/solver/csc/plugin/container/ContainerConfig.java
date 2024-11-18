package qilin.core.solver.csc.plugin.container;



import pascal.taie.collection.*;
import pascal.taie.util.Indexer;
import qilin.core.PTAScene;
import qilin.core.pag.AllocNode;
import qilin.core.pag.ContextAllocNode;
import qilin.core.solver.csc.plugin.container.HostMap.HostList;
import soot.*;
import soot.jimple.InvokeExpr;

import java.util.*;
import java.util.stream.Stream;

import static qilin.core.solver.csc.plugin.container.ClassAndTypeClassifier.ClassificationOf;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.COL_0;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.COL_ITR;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_0;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_ENTRY;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_ENTRY_ITR;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_ENTRY_SET;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_KEY_ITR;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_KEY_SET;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_VALUES;
import static qilin.core.solver.csc.plugin.container.HostMap.HostList.Kind.MAP_VALUE_ITR;

public class ContainerConfig {
    private static final Hierarchy hierarchy = Scene.v().getActiveHierarchy();
    private final static Map<SootClass, List<SootClass>> allSubClasses = Maps.newMap();
    public static ContainerConfig config = new ContainerConfig();

    private final TwoKeyMap<SootMethod, Integer, Parameter> parameters = Maps.newTwoKeyMap();
    private final Map<Parameter, RefType> ParameterOfColValue = Maps.newMap();

    private final Map<Parameter, RefType> ParameterOfMapValue = Maps.newMap();
    private final Map<Parameter, RefType> ParameterOfMapKey = Maps.newMap();

    private final Set<String> excludedContainers = Sets.newSet();
    private final Set<String> keySet = Sets.newSet();
    private final Set<String> valueSet = Sets.newSet();

    private final Set<String> unrelatedInInvokes = Sets.newSet();

    private final Set<SootMethod> OutMethodsOfMapKey = Sets.newSet();
    private final Set<SootMethod> OutMethodsOfMapValue = Sets.newSet();
    private final Set<SootMethod> OutMethodsOfColValue = Sets.newSet();

    private final Set<SootClass> iteratorClasses = Sets.newSet();

    private final HostManager hostManager = new HostManager();

    private Map<InvokeExpr, Integer> invokeToLineNumer;


    public static List<SootClass> getAllSubClasses(SootClass superClz){
        if(superClz.isInterface()){
            return allSubClasses.computeIfAbsent(superClz, hierarchy::getImplementersOf);
        }else
            return allSubClasses.computeIfAbsent(superClz, hierarchy::getSubclassesOf);
    }


    public void setInvokeToLineNumer(Map<InvokeExpr, Integer> invokeToLineNumer) {
        this.invokeToLineNumer = invokeToLineNumer;
    }

    /**
     * Map correlation-extending JMethods to their parameters
     */
    private final MultiMap<SootMethod, Pair<Integer, Integer>> corExtenders = Maps.newMultiMap(); // parameters in methods which are similar to "putAll"/"addAll"

    private final Map<SootMethod, Pair<Integer, Integer>> arrayInitializer = Maps.newMap();

    private static final TwoKeyMap<HostList.Kind, String, HostList.Kind> hostGenerators = Maps.newTwoKeyMap();

    private final Set<SootClass> hostClasses = Sets.newSet();

    private static final TwoKeyMap<HostList.Kind, String, String> NonContainerExits = Maps.newTwoKeyMap();

    private final MultiMap<SootClass, SootClass> allocSiteOfEntrySet = Maps.newMultiMap();

    private final Set<SootClass> taintClasses = Sets.newSet();

    private final Set<SootClass> taintAbstractListClasses = Sets.newSet();

    static { // initialize hostGenerators (host-passer means pass a host to lhs at an invoke when the base-variable has a required kind)
        hostGenerators.put(COL_0, "iterator", COL_ITR);
        hostGenerators.put(COL_0, "Iterator", COL_ITR);
        hostGenerators.put(MAP_0, "entrySet", MAP_ENTRY_SET);
        hostGenerators.put(MAP_0, "keySet", MAP_KEY_SET);
        hostGenerators.put(MAP_0, "KeySet", MAP_KEY_SET);
        hostGenerators.put(MAP_0, "values", MAP_VALUES);
        hostGenerators.put(MAP_0, "Entry", MAP_ENTRY);
        hostGenerators.put(MAP_0, "keys", MAP_KEY_ITR);
        hostGenerators.put(MAP_ENTRY_SET, "iterator", MAP_ENTRY_ITR);
        hostGenerators.put(MAP_VALUES, "iterator", MAP_VALUE_ITR);
        hostGenerators.put(MAP_KEY_SET, "iterator", MAP_KEY_ITR);
        hostGenerators.put(MAP_ENTRY_ITR, "next", MAP_ENTRY);

        NonContainerExits.put(MAP_ENTRY, "getValue", "Map-Value");
        NonContainerExits.put(MAP_ENTRY, "getKey", "Map-Key");
        NonContainerExits.put(MAP_KEY_ITR, "next", "Map-Key");
        NonContainerExits.put(MAP_KEY_ITR, "nextElement", "Map-Key");
        NonContainerExits.put(MAP_VALUE_ITR, "next", "Map-Value");
        NonContainerExits.put(MAP_VALUE_ITR, "nextElement", "Map-Value");
        NonContainerExits.put(COL_ITR, "next", "Col-Value");
        NonContainerExits.put(COL_ITR, "nextElement", "Col-Value");
        NonContainerExits.put(COL_ITR, "previous", "Col-Value");
    }

    public boolean isTaintType(Type type) {
        if (type instanceof RefType classType) {
//            return ClassificationOf(type) != ClassAndTypeClassifier.containerType.OTHER && classType.getJClass().isApplication();
            return taintClasses.contains(classType.getSootClass());
        }
        return false;
    }

    public boolean isRealHostClass(SootClass clz) {
        return hostClasses.contains(clz);
    }

    public boolean isTaintAbstractListType(Type type) {
        if (type instanceof RefType classType) {
            return taintAbstractListClasses.contains(classType.getSootClass());
        }
        return false;
    }

    public Stream<SootClass> taintAbstractListClasses() {
        return taintAbstractListClasses.stream();
    }

    public void addHostClass(String clz) {
        SootClass c = getSootClass(clz);
        if (c != null && !c.isAbstract())
            hostClasses.add(c);
    }

    private SootClass getSootClass(String clz){
        try {
            return PTAScene.v().getSootClass(clz);
        }catch (IllegalStateException e){
            System.err.println("class:\t" + clz + " not exists");
            return null;
        }
    }


    public void computeTaintClass() {
        SootClass c = getSootClass("java.util.Collection"), ca = getSootClass("java.util.AbstractList");
        if(c != null){
         getAllSubClasses(c).forEach(clz -> {
                if (!clz.isAbstract() && !hostClasses.contains(clz) && !excludedContainers.contains(clz.getName())) {
                    if(hierarchy.isClassSuperclassOf(ca, clz))
                        taintAbstractListClasses.add(clz);
                    else
                        taintClasses.add(clz);
                }
            });
        }

        c = getSootClass("java.util.Map");
        getAllSubClasses(c).forEach(clz -> {
            if (!clz.isAbstract() && !hostClasses.contains(clz) && !excludedContainers.contains(clz.getName())) {
                taintClasses.add(clz);
            }
        });
    }

    public static void setConfig(ContainerConfig config) {
        ContainerConfig.config = config;
    }

    public SootMethod getMethod(String signature) {
        try{
            return Scene.v().getMethod(signature);
        }catch (RuntimeException e){
            return null;
        }

    }

    public Parameter getParameter(SootMethod method, int index) {
        if (parameters.get(method, index) == null) {
            parameters.put(method, index, new Parameter(method, index));
        }
        return parameters.get(method, index);
    }

    public void addParameterWithType(Parameter parameter, RefType classType, String type) {
        if (classType == null)
            return;
        switch (type) {
            case "Map-Key" -> {
                if (ParameterOfMapKey.get(parameter) != null)
                    throw new RuntimeException("Multiple classType for parameter: " + parameter);
                ParameterOfMapKey.put(parameter, classType);
            }
            case "Map-Value" -> {
                if (ParameterOfMapValue.get(parameter) != null) {
                    throw new RuntimeException("Multiple classType for parameter: " + parameter);
                }
                ParameterOfMapValue.put(parameter, classType);
            }
            case "Col-Value" -> {
                if (ParameterOfColValue.get(parameter) != null)
                    throw new RuntimeException("Multiple classType for parameter: " + parameter);
                ParameterOfColValue.put(parameter, classType);
            }
        }
    }

    public void addInParameter(String SMethod, int index, String type) {
        try {
            SootMethod method = Scene.v().getMethod(SMethod);
            if (method == null) {
                return;
            }
            Parameter parameter = getParameter(method, index);
            RefType classType = method.getDeclaringClass().getType();
            addInParameter(parameter, classType, type);
        }catch (RuntimeException e){
            return;
        }

    }

    public void addInParameter(String SMethod, int index, String type, String jClass) {
        try{
            SootMethod method = Scene.v().getMethod(SMethod);

            Parameter parameter = getParameter(method, index);
            SootClass jClass1 = getSootClass(jClass);
            if(jClass1 != null){
                RefType classType = Objects.requireNonNull(getSootClass(jClass)).getType();
                addInParameter(parameter, classType, type);
            }
        }catch (RuntimeException e){
            // method not exist.
        }


    }

    public void excludeClass(String clz) {
        excludedContainers.add(clz);
    }

    public void addKeySetClass(String clz) {
        keySet.add(clz);
    }

    public void addValueSetClass(String clz) {
        valueSet.add(clz);
    }

    public boolean isKeySetClass(String clz) {
        return keySet.contains(clz);
    }

    public boolean isValueSetClass(String clz) {
        return valueSet.contains(clz);
    }

    private void addInParameter(Parameter parameter, RefType classType, String type) {
        switch(type) {
            case "Map-Key", "Map-Value", "Col-Value" -> addParameterWithType(parameter, classType, type);
            default -> throw new RuntimeException("No such parameters!");
        }
    }

    public RefType getTypeConstraintOf(SootMethod method, int index, String type) {
        Parameter parameter = getParameter(method, index);
        switch (type) {
            case "Map-Key" -> {
                return ParameterOfMapKey.get(parameter);
            }
            case "Map-Value" -> {
                return ParameterOfMapValue.get(parameter);
            }
            case "Col-Value" -> {
                return ParameterOfColValue.get(parameter);
            }
            default -> {
                return null;
            }
        }
    }

    public void addMapKeyExit(String... methods) {
        for (String method: methods){
            SootMethod method1 = getMethod(method);
            if(method1 != null){
                OutMethodsOfMapKey.add(method1);
            }
        }

    }

    public String CategoryOfExit(SootMethod method) {
        if (OutMethodsOfMapKey.contains(method))
            return "Map-Key";
        if (OutMethodsOfMapValue.contains(method))
            return "Map-Value";
        if (OutMethodsOfColValue.contains(method))
            return "Col-Value";
        return "Other";
    }

    public void addMapValueOutMethod(String... methods)  {
        for (String method: methods) {
            SootMethod m = getMethod(method);
            if (m != null) {
                OutMethodsOfMapValue.add(m);
            }
        }
    }

    public void addCollectionValueOutMethod(String... methods) {
        for (String method: methods) {
            SootMethod m = getMethod(method);
            if (m != null) {
                OutMethodsOfColValue.add(m);
            }
        }
    }

    public void addCorrelationExtender(String inMethod, int index0, int index1) {
        addCorrelationExtender(getMethod(inMethod), index0, index1);
    }

    public void addCorrelationExtender(SootMethod inMethod, int index0, int index1) {
        if (inMethod != null)
            corExtenders.put(inMethod, new Pair<>(index0, index1));
    }

    public void addArrayInitializer(String smethod, int index0, int index1) {
        // index0: index of array variable, index1: index of Collection variable
        SootMethod method = getMethod(smethod);
        if(method != null){
            arrayInitializer.put(method, new Pair<>(index0, index1));
        }

    }

    public boolean isCorrelationExtender(SootMethod inMethod) {
        return corExtenders.get(inMethod).size() > 0;
    }

    public boolean isHostType(RefType type) {
        return ClassificationOf(type) != ClassAndTypeClassifier.containerType.OTHER && !excludedContainers.contains(type.getClassName());
    }

    public Host getObjectHost(AllocNode obj, Host.Classification classification) {
        if (!isHostType((RefType) obj.getType())) {
            System.err.println("Warning: " + obj.getType() + " is not an outer class!");
            return null;
        }
        else {
            return hostManager.getHost(obj, classification);
        }
    }

    public int getHostCount() {
        return hostManager.getHostCount();
    }

    public int getHostClassCount(){
        return hostClasses.size();
    }

    public void addUnrelatedInvoke(String invoke) {
        unrelatedInInvokes.add(invoke);
    }

    public boolean isUnrelatedInInvoke(SootMethod container, InvokeExpr invoke) {
        if(!invokeToLineNumer.containsKey(invoke)){
            return false;
        }
        return unrelatedInInvokes.contains(container + "/" + invokeToLineNumer.get(invoke));
    }

    public Set<Pair<Integer, Integer>> getCorrelationExtender(SootMethod method) {
        return corExtenders.get(method);
    }

    public Pair<Integer, Integer> getArrayInitializer(SootMethod method) {
        return arrayInitializer.get(method);
    }

    public static TwoKeyMap<HostList.Kind, String, HostList.Kind> getHostGenerators() {
        return hostGenerators;
    }

    public static TwoKeyMap<HostList.Kind, String, String> getNonContainerExits() {
        return NonContainerExits;
    }

    public void addIteratorClass(String clz) {
        SootClass iclz = getSootClass(clz);
        if (clz != null)
            iteratorClasses.add(iclz);
    }

    public boolean isIteratorClass(SootClass clz) {
        return iteratorClasses.contains(clz);
    }

    public void addAllocationSiteOfEntrySet(String entrySet, String allocClass) {
        SootClass c1 = getSootClass(entrySet), c2 = getSootClass(allocClass);
        if (c1 == null || c2 == null) {
//            logger.warn("Invalid info about EntrySet: {} and {}", entrySet, allocClass);
        }
        else
            addAllocationSiteOfEntrySet(c1, c2);
    }

    private void addAllocationSiteOfEntrySet(SootClass entrySet, SootClass allocClass) {
        // An EntrySet Class may have several allocation Sites which allocate its elements
        // We only specified the declaring class of the container of allocation Sites.
        allocSiteOfEntrySet.put(allocClass, entrySet);
        entrySetClasses.add(entrySet);
    }

    public Set<SootClass> getRelatedEntrySetClassesOf(SootClass allocClass) {
        return allocSiteOfEntrySet.get(allocClass);
    }

    private final Set<SootClass> entrySetClasses = Sets.newSet();

    public Set<SootClass> getAllEntrySetClasses() {
        return Collections.unmodifiableSet(entrySetClasses);
    }

    public boolean isEntrySetClass(SootClass clz) {
        return entrySetClasses.contains(clz);
    }

    public Indexer<Host> getHostIndexer() {
        return hostManager;
    }

    private static class HostManager implements Indexer<Host> {

        private final Map<AllocNode, Host> objHostMap = Maps.newMap();

        private Host[] hosts = new Host[8092];

        private int counter = 0;

        Host getHost(AllocNode obj, Host.Classification classification) {
            if(obj instanceof ContextAllocNode){
                throw new RuntimeException("Context alloc node detected.");
            }
            return objHostMap.computeIfAbsent(obj, o -> {
                int index = counter ++;
                Host host = new Host(o, index, classification);
                storeHost(host, index);
                return host;
            });
        }

        private void storeHost(Host host, int index) {
            if (index >= hosts.length) {
                int newLength = Math.max(index + 1, (int) (hosts.length * 1.5));
                Host[] oldArray = hosts;
                hosts = new Host[newLength];
                System.arraycopy(oldArray, 0, hosts, 0, oldArray.length);
            }
            hosts[index] = host;
        }

        @Override
        public int getIndex(Host o) {
            return o.getIndex();
        }

        @Override
        public Host getObject(int index) {
            return hosts[index];
        }

        public int getHostCount() {
            return counter;
        }
    }
}
