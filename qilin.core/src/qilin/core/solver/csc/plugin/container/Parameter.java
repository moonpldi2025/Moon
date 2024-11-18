package qilin.core.solver.csc.plugin.container;

import soot.SootMethod;

public record Parameter(SootMethod method, int index) {

    @Override
    public String toString() {
        return "Parameter[" + index + "@" + method + "]";
    }

}
