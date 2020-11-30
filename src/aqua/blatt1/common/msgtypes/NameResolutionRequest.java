package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionRequest implements Serializable {
    public final String requestedTank;
    public final String requestId;

    public NameResolutionRequest(String tankId, String requestId) {
        this.requestedTank = tankId;
        this.requestId = requestId;
    }
}
