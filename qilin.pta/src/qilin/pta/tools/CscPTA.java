package qilin.pta.tools;

import qilin.CoreConfig;
import qilin.core.CorePTA;
import qilin.core.builder.CSCCallGraphBuilder;
import qilin.core.builder.CallGraphBuilder;
import qilin.core.pag.CSCPAG;
import qilin.core.pag.PAG;
import qilin.core.solver.Propagator;
import qilin.core.solver.Solver;
import qilin.core.solver.csc.CutShortcutSolver;
import qilin.parm.ctxcons.InsensCtxConstructor;
import qilin.parm.heapabst.AllocSiteAbstractor;
import qilin.parm.heapabst.HeuristicAbstractor;
import qilin.parm.select.InsenSelector;
import qilin.pta.PTAConfig;
import qilin.stat.IEvaluator;
import qilin.stat.SimplifiedEvaluator;
import qilin.util.PTAUtils;



public class CscPTA extends BasePTA {
    public CscPTA() {
        this.ctxCons = new InsensCtxConstructor();
        this.ctxSel = new InsenSelector();
        if (PTAConfig.v().getPtaConfig().mergeHeap) {
            this.heapAbst = new HeuristicAbstractor(pag);
        } else {
            this.heapAbst = new AllocSiteAbstractor();
        }
        System.out.println("Context-Insensitive ...");
    }


    @Override
    protected PAG createPAG() {
        return new CSCPAG(this);
    }

    @Override
    protected CallGraphBuilder createCallGraphBuilder() {
        return new CSCCallGraphBuilder(this);
    }

    @Override
    public Propagator getPropagator() {
        CutShortcutSolver solver = new CutShortcutSolver(this);
        CSCPAG cscpag = (CSCPAG) this.pag;
        cscpag.setCutShortcutSolver(solver);
        return solver;
    }
}

