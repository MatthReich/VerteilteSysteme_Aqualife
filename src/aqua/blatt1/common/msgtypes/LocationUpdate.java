package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class LocationUpdate implements Serializable {
    private final String fishId;
    private final InetSocketAddress newAddress;



    public LocationUpdate(String fishId, InetSocketAddress newAddress) {
        this.fishId = fishId;
        this.newAddress = newAddress;
    }

    public String getFishId() {
        return fishId;
    }

    public InetSocketAddress getNewAddress() {
        return newAddress;
    }
}
