package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class LocationRequest implements Serializable {
    public String fishId;

    public LocationRequest(String fishId) {
        this.fishId = fishId;
    }
}
