package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class NeighborUpdate implements Serializable {
    private final InetSocketAddress fishR;
    private final InetSocketAddress fishL;

    public NeighborUpdate(InetSocketAddress fishL, InetSocketAddress fishR) {
        this.fishR = fishR;
        this.fishL = fishL;
    }

    public InetSocketAddress getFishL() {
        return fishL;
    }

    public InetSocketAddress getFishR() {
        return fishR;
    }


}
