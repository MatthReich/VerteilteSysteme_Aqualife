package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionRequest implements Serializable {
    public final InetSocketAddress requestedTank;
    public final String requestId;

    public NameResolutionRequest(InetSocketAddress requestedTank, String requestId) {
        this.requestedTank = requestedTank;
        this.requestId = requestId;
    }
}
