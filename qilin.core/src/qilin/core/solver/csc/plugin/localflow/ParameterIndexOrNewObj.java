package qilin.core.solver.csc.plugin.localflow;

import qilin.core.pag.AllocNode;
import qilin.core.pag.ContextAllocNode;
import qilin.core.solver.csc.plugin.field.ParameterIndex;

public record ParameterIndexOrNewObj(boolean isObj, ParameterIndex index, ContextAllocNode csObj){
    public static ParameterIndexOrNewObj INDEX_THIS = new ParameterIndexOrNewObj(false, ParameterIndex.THISINDEX, null);

    @Override
    public String toString() {
        return isObj ? csObj.toString() : index.toString();
    }
}
