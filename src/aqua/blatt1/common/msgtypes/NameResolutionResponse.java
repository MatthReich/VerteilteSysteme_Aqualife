package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionResponse implements Serializable {
    public final InetSocketAddress requestedTank;
    public final String requestId;

    public NameResolutionResponse(InetSocketAddress tankAddress, String requestId) {
        this.requestedTank = tankAddress;
        this.requestId = requestId;
    }
}
