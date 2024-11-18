package qilin.core.solver.csc.plugin.field;

import qilin.core.pag.Field;
import qilin.core.pag.LocalVarNode;
import soot.jimple.spark.pag.SparkField;

public class AbstractLoadField {
    private final boolean terminate;
    private final LocalVarNode loadedToVar;
    private final Field field;
    private final LocalVarNode baseVar;
    public AbstractLoadField(LocalVarNode loadedToVar, Field field, LocalVarNode baseVar, boolean terminate){
        this.terminate = terminate;
        this.loadedToVar = loadedToVar;
        this.field = field;
        this.baseVar = baseVar;
    }

    public LocalVarNode getBaseVar() {
        return baseVar;
    }

    public LocalVarNode getLoadedToVar() {
        return loadedToVar;
    }

    public Field getField() {
        return field;
    }

    public boolean isNonRelay() {
        return !terminate;
    }
}
