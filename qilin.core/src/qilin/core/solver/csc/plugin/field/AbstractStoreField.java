package qilin.core.solver.csc.plugin.field;

import qilin.core.pag.Field;
import qilin.core.pag.LocalVarNode;
import soot.jimple.spark.pag.SparkField;

public class AbstractStoreField {
    private final LocalVarNode storeFromVar;
    private final Field field;
    private final LocalVarNode baseVar;
    public AbstractStoreField(LocalVarNode storeFromVar, Field field, LocalVarNode baseVar){
        this.storeFromVar = storeFromVar;
        this.field = field;
        this.baseVar = baseVar;
    }

    public LocalVarNode getBaseVar() {
        return baseVar;
    }

    public LocalVarNode getStoreFromVar() {
        return storeFromVar;
    }

    public Field getField() {
        return field;
    }

}

