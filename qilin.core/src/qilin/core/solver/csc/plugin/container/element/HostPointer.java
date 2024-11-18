package qilin.core.solver.csc.plugin.container.element;

import qilin.core.pag.ValNode;
import qilin.core.solver.csc.plugin.container.Host;
import soot.Type;

public class HostPointer extends ValNode {
    private final int index;
    private final Host host;
    private final String category;
    public HostPointer(Host host, String category, int index){
        super(host.getType());
        this.index = index;
        this.host = host;
        this.category = category;
    }

    public Host getHost() {
        return host;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return host.toString();
    }
}
