package inz.agents;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import inz.util.AgentPos;

/**
 * Created by Kuba on 02/05/2017.
 */


public interface MobileAgentInterface extends Serializable {

    enum State {GATHER, CHOOSE, VOTE, LEAD}

    void changeState(State newState);

    void updateLocation(final Location location);

    void startLocationBroadcast();

    ArrayList<AgentPos> getGroup();

    AgentPos getCenter();

    void setCenter(LatLng newCenterPos);

    void addPlace(String name, LatLng pos);

    ArrayList<AgentPos>  getPlaces();

    void addVote(String markerName);

    HashMap<String, Integer> getVotes();

    void choosePlace(String name);

    AgentPos getDestination();

    int getMaxVotes ();
}
