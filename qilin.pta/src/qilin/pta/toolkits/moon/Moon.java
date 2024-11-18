package qilin.pta.toolkits.moon;

import qilin.core.PTA;
import qilin.core.pag.AllocNode;
import qilin.pta.toolkits.moon.ContextMatch.ContextMatcherNew;

import java.util.Set;

public class Moon {

    protected Set<AllocNode> ctxDepHeaps = null;


    public Moon(PTA pta, int maxMatchLayer){
        System.out.println("Moon starts: maxMatchLayer = " + maxMatchLayer);
        this.ctxDepHeaps = new ContextMatcherNew(maxMatchLayer, pta).match();
    }


    public Set<AllocNode> getCtxDepHeaps() {
        if(ctxDepHeaps == null){
            throw new RuntimeException("Context Dependent Heaps have not been computed yet");
        }
        return ctxDepHeaps;
    }
}
