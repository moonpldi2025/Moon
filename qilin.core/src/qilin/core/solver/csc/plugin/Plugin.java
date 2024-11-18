package qilin.core.solver.csc.plugin;

import qilin.core.pag.*;
import qilin.core.sets.PointsToSetInternal;
import qilin.core.solver.csc.CutShortcutSolver;
import qilin.core.solver.csc.plugin.container.HostMap.HostList;
import qilin.core.solver.csc.plugin.container.HostMap.HostSet;
import qilin.core.solver.csc.plugin.field.ParameterIndex;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.Set;

public interface Plugin {


    default void onStart(){}
    default void onFinish(){}
    default void setSolver(CutShortcutSolver solver){}
    default void onNewMethod(SootMethod method) {
    }

    default void onNewPointsToSet(ValNode pointer, Set<AllocNode> newPts){

    }

    default void onNewCallEdge(Edge edge){}

    default void onNewNonRelayLoadEdge(Node oDotF, Node to){}

    default void onNewSetStatement(SootMethod method, Field field, ParameterIndex baseIndex, ParameterIndex rhsIndex){}
    default void onNewGetStatement(SootMethod method, ParameterIndex baseIndex, Field field){}

    default void onNewPFGEdge(Node src, Node tgt){

    }

    default void onNewHostEntry(Node csVar, HostList.Kind kind, HostSet hostset){}
}
