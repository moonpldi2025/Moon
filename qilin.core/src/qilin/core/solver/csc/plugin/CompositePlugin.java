package qilin.core.solver.csc.plugin;

import qilin.core.pag.*;
import qilin.core.solver.csc.CutShortcutSolver;
import qilin.core.solver.csc.plugin.container.HostMap.HostList;
import qilin.core.solver.csc.plugin.container.HostMap.HostSet;
import qilin.core.solver.csc.plugin.field.ParameterIndex;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompositePlugin implements Plugin {

    private final List<Plugin> allPlugins = new ArrayList<>();

    // Use separate lists to store plugins that overwrite
    // frequently-invoked methods.

    private final List<Plugin> onNewPointsToSetPlugins = new ArrayList<>();

    private final List<Plugin> onNewCallEdgePlugins = new ArrayList<>();

    private final List<Plugin> onNewMethodPlugins = new ArrayList<>();


    private final List<Plugin> onNewPFGEdgePlugins = new ArrayList<>();

    private final List<Plugin> onNewSetStatementPlugins = new ArrayList<>();

    private final List<Plugin> onNewGetStatementPlugins = new ArrayList<>();

    private final List<Plugin> onNewHostEntryPlugins = new ArrayList<>();

    private final List<Plugin> onNewNonRelayLoadPlugins = new ArrayList<>();

    public void addPlugin(Plugin... plugins) {
        for (Plugin plugin : plugins) {
            allPlugins.add(plugin);
            addPlugin(plugin, onNewPointsToSetPlugins,
                    "onNewPointsToSet", ValNode.class, Set.class);
            addPlugin(plugin, onNewCallEdgePlugins, "onNewCallEdge", Edge.class);
            addPlugin(plugin, onNewMethodPlugins, "onNewMethod", SootMethod.class);
            addPlugin(plugin, onNewPFGEdgePlugins, "onNewPFGEdge", Node.class, Node.class);
            addPlugin(plugin, onNewSetStatementPlugins, "onNewSetStatement", SootMethod.class, Field.class, ParameterIndex.class, ParameterIndex.class);
            addPlugin(plugin, onNewGetStatementPlugins, "onNewGetStatement", SootMethod.class, ParameterIndex.class, Field.class);
            addPlugin(plugin, onNewHostEntryPlugins, "onNewHostEntry", Node.class, HostList.Kind.class, HostSet.class);
            addPlugin(plugin, onNewNonRelayLoadPlugins, "onNewNonRelayLoadEdge", Node.class, Node.class);
        }
    }

    private void addPlugin(Plugin plugin, List<Plugin> plugins,
                           String name, Class<?>... parameterTypes) {
        try {
            Method method = plugin.getClass().getMethod(name, parameterTypes);
            if (!method.getDeclaringClass().equals(Plugin.class)) {
                // the plugin does overwrite the specific method
                plugins.add(plugin);
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Can't find method '" + name +
                    "' in " + plugin.getClass(), e);
        }
    }

    @Override
    public void setSolver(CutShortcutSolver solver) {
        allPlugins.forEach(p -> p.setSolver(solver));
    }

    @Override
    public void onStart() {
        allPlugins.forEach(Plugin::onStart);
    }

    @Override
    public void onFinish() {
        allPlugins.forEach(Plugin::onFinish);
    }

    @Override
    public void onNewPointsToSet(ValNode csVar, Set<AllocNode> pts) {
        onNewPointsToSetPlugins.forEach(p -> p.onNewPointsToSet(csVar, pts));
    }

    @Override
    public void onNewCallEdge(Edge edge) {
        onNewCallEdgePlugins.forEach(p -> p.onNewCallEdge(edge));
    }

    @Override
    public void onNewMethod(SootMethod method) {
        onNewMethodPlugins.forEach(p -> p.onNewMethod(method));
    }



    @Override
    public void onNewPFGEdge(Node src, Node tgt) {
        onNewPFGEdgePlugins.forEach(p -> p.onNewPFGEdge(src, tgt));
    }


    @Override
    public void onNewSetStatement(SootMethod method, Field field, ParameterIndex baseIndex, ParameterIndex rhsIndex) {
        onNewSetStatementPlugins.forEach(p -> p.onNewSetStatement(method, field, baseIndex, rhsIndex));
    }

    @Override
    public void onNewGetStatement(SootMethod method, ParameterIndex baseIndex, Field field) {
        onNewGetStatementPlugins.forEach(p -> p.onNewGetStatement(method, baseIndex, field));
    }

    @Override
    public void onNewHostEntry(Node csVar, HostList.Kind kind, HostSet hostset) {
        onNewHostEntryPlugins.forEach(p -> p.onNewHostEntry(csVar, kind, hostset));
    }

    @Override
    public void onNewNonRelayLoadEdge(Node oDotF, Node to) {
        onNewNonRelayLoadPlugins.forEach(p -> p.onNewNonRelayLoadEdge(oDotF, to));
    }
}

