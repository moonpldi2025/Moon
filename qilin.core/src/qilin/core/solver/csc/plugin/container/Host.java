package qilin.core.solver.csc.plugin.container;


import pascal.taie.collection.Maps;
import pascal.taie.collection.Sets;
import qilin.core.pag.AllocNode;
import qilin.core.pag.LocalVarNode;
import soot.Type;

import java.util.Map;
import java.util.Set;

public class Host  {
    private final AllocNode obj;

    private boolean taint;

    private final int index;

    private final Map<String, Set<LocalVarNode>> relatedArguments = Maps.newMap();

    private final Map<String, Set<LocalVarNode>> relatedResults = Maps.newMap();

    public enum Classification {
        MAP, COLLECTION
    }

    private Classification classification;

    public Host(AllocNode obj, int index, Classification classification) {
        this.obj = obj;
        this.index = index;
        this.classification = classification;
        taint = false;
        relatedArguments.put("Map-Value", Sets.newSet());
        relatedArguments.put("Map-Key", Sets.newSet());
        relatedArguments.put("Col-Value", Sets.newSet());
        relatedResults.put("Map-Value", Sets.newSet());
        relatedResults.put("Map-Key", Sets.newSet());
        relatedResults.put("Col-Value", Sets.newSet());
    }

    public void setTaint() {
        taint = true;
    }

    public boolean getTaint() {
        return taint;
    }

    public int getIndex() {
        return index;
    }

    public boolean addInArgument(LocalVarNode var, String category) {
        Set<LocalVarNode> arguments = relatedArguments.get(category);
        if (arguments == null)
            throw new RuntimeException("Invalid Category!");
        return arguments.add(var);
    }


    public boolean addOutResult(LocalVarNode var, String category) {
        Set<LocalVarNode> results = relatedResults.get(category);
        return results != null && results.add(var);
    }

    public AllocNode getObject() {
        return obj;
    }

    public Type getType() {
        return obj.getType();
    }

    public Classification getClassification() {
        return classification;
    }

    @Override
    public String toString() {
        return "Host-Object{" + obj.toString() + "}";
    }
}
