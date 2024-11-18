package qilin.core.solver.csc.plugin.field;

import qilin.core.pag.Field;
import soot.jimple.spark.pag.SparkField;

import java.util.Objects;

public record SetStatement(ParameterIndex baseIndex, Field field, ParameterIndex rhsIndex) {

    @Override
    public String toString() {
        return "[setStmt]" + baseIndex().toString() + "." + field().toString() + " = " + rhsIndex().toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(rhsIndex, baseIndex, field);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj instanceof SetStatement other){
            return this.rhsIndex == other.rhsIndex()
                    && this.baseIndex == other.baseIndex
                    && this.field == other.field;
        }
        return false;
    }
}
