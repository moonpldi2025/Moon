package qilin.core.builder;


import pascal.taie.collection.*;
import qilin.core.pag.*;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;

import java.util.*;
import java.util.stream.Collectors;

public class MethodCallDetail {
    public static final Object STATIC_OBJ_CTX = new Object();
    private final MultiMap<SootMethod, Pair<Object, SootMethod>> calleeToCtxAndCaller = Maps.newConcurrentMultiMap();
    private final TwoKeyMap<SootMethod, SootMethod, Set<Object>> callerCalleeToRecvObj = Maps.newConcurrentTwoKeyMap();

    private final MultiMap<SootMethod, SootMethod> callerToCallee = Maps.newConcurrentMultiMap();
    private final MultiMap<SootMethod, SootMethod> calleeToCaller = Maps.newConcurrentMultiMap();

    private final MultiMap<LocalVarNode, InstanceInvokeExpr> recvToInvokeExprs = Maps.newConcurrentMultiMap();


    private final TwoKeyMultiMap<LocalVarNode, LocalVarNode, Value> argToParamToRecvValue = Maps.newConcurrentTwoKeyMultiMap();

    private static final MethodCallDetail instance = new MethodCallDetail();
    public static MethodCallDetail v() {
        return instance;
    }



    private boolean enabled = false;
    private boolean initialized = false;
    public void enable() {
        enabled = true;
        initialized = true;
    }
    public void disable(){
        enabled = false;
    }
    private MethodCallDetail() {
    }

    public void addCalleeToCtxAndCaller(SootMethod callee, Object ctx, SootMethod caller) {
        if(!enabled) return;
        callerToCallee.put(caller, callee);
        calleeToCaller.put(callee, caller);
        if(ctx instanceof AllocNode || ctx.equals(STATIC_OBJ_CTX)) {
            if(ctx instanceof ContextAllocNode){
                ctx = ((ContextAllocNode) ctx).base();
            }
            calleeToCtxAndCaller.put(callee, new Pair<>(ctx, caller));
            if(!callerCalleeToRecvObj.containsKey(caller, callee)){
                callerCalleeToRecvObj.put(caller, callee, Sets.newSet());
            }
            Set<Object> recvObjs = callerCalleeToRecvObj.get(caller, callee);
            recvObjs.add(ctx);
        }
        else
            throw new RuntimeException("Unknown context type");
    }

    private void checkInitialized(){
        if(!initialized) throw new RuntimeException("MethodCallDetail is not initialized");
    }
    public Collection<Pair<Object, SootMethod>> usageCtxAndCallerOf(SootMethod callee) {
        checkInitialized();
        return calleeToCtxAndCaller.get(callee);
    }





    public void addInvokeExpr(LocalVarNode receiver, InstanceInvokeExpr invokeExpr){
        recvToInvokeExprs.put(receiver, invokeExpr);
    }


    public boolean isReceiver(LocalVarNode node){
        checkInitialized();
        return recvToInvokeExprs.containsKey(node);
    }
    public boolean isReceiver(LocalVarNode node, AllocNode obj){
        checkInitialized();
//        return recvToInvokeExprs.containsKey(node);
        if(!recvToInvokeExprs.containsKey(node)) return false;
        if(obj.getType() instanceof ArrayType) return false;
        Set<InstanceInvokeExpr> invokeExprs = recvToInvokeExprs.get(node);
        if(invokeExprs.isEmpty()) throw new RuntimeException("No method invoked on the receiver where it should not");
        return invokeExprs.stream().allMatch(invokeExpr -> calleeCache.containsKey(obj, invokeExpr));
    }


    private final TwoKeyMap<AllocNode, InvokeExpr, SootMethod> calleeCache = Maps.newTwoKeyMap();

    public void addCalleeCache(AllocNode recvObj, InvokeExpr invokeExpr, SootMethod callee){
        if(calleeCache.containsKey(recvObj, invokeExpr)){
            throw new RuntimeException("Callee Cache already contains the key");
        }
        calleeCache.put(recvObj, invokeExpr, callee);
    }
    public SootMethod resolveCallee(AllocNode recvObj, InvokeExpr invokeExpr){
        return calleeCache.get(recvObj, invokeExpr);
    }


    public void addArgToParamToRecvValue(Node arg, Node param, Value recvValue){
        if(arg instanceof ContextVarNode contextVarNode){
            arg = contextVarNode.base();
        }
        if(param instanceof ContextVarNode contextVarNode){
            param = contextVarNode.base();
        }
        if(!argToParamToRecvValue.containsKey((LocalVarNode) arg, (LocalVarNode) param) || argToParamToRecvValue.get((LocalVarNode) arg, (LocalVarNode) param).size() < 2){
            argToParamToRecvValue.put((LocalVarNode) arg, (LocalVarNode) param, recvValue);
        }

    }

    public Set<Value> getRecvValueOfArgAndParam(LocalVarNode arg, LocalVarNode param){
        return argToParamToRecvValue.get(arg, param);
    }

}
