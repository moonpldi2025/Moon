package qilin.core.solver.csc.plugin.field;

import qilin.core.pag.Field;
import qilin.core.solver.csc.plugin.Plugin;
import soot.jimple.spark.pag.SparkField;

import java.util.Objects;

public record GetStatement(ParameterIndex baseIndex, Field field)  {
    @Override
    public String toString() {
        return "[GetStmt]" + "=" + baseIndex + "." + field;
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseIndex, field);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj instanceof GetStatement other){
            return this.baseIndex == other.baseIndex
                    && this.field == other.field;
        }
        return false;
    }
}
