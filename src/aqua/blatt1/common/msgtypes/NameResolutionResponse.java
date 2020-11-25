package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionResponse implements Serializable {
    public final InetSocketAddress requestedTank;
    public final String requestId;

    public NameResolutionResponse(InetSocketAddress requestedTank, String requestId) {
        this.requestedTank = requestedTank;
        this.requestId = requestId;
    }
}
