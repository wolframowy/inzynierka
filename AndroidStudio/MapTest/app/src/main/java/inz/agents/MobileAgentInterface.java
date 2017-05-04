package inz.agents;

import android.location.Location;

import java.io.Serializable;

/**
 * Created by Kuba on 02/05/2017.
 */

public interface MobileAgentInterface extends Serializable {

    public void updateLocation(final Location location);

    public void startLocationBroadcast();
}
