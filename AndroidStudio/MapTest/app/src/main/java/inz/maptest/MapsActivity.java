package inz.maptest;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import inz.agents.MobileAgentInterface;
import inz.util.AgentPos;
import jade.core.MicroRuntime;
import jade.wrapper.ControllerException;

import static inz.maptest.R.id.map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnMarkerDragListener,
        GoogleMap.OnMarkerClickListener,
        LocationListener {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;
    private Map<String, Marker> mGroupMarkerMap;
    private Map<String, Marker> mPlacesMarkerMap;
    private Marker mCurrSelectedPlace;
    private Marker mCenterMarker;
    private Marker mDestMarker;
    private boolean mCenterDraggable = true;
    private int PLACE_PICKER = 1;

    private MyReceiver myReceiver;

    private MobileAgentInterface agentInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        String nickname = getIntent().getStringExtra("AGENT_NICKNAME");

        try {
            agentInterface = MicroRuntime.getAgent(nickname).getO2AInterface(MobileAgentInterface.class);
        } catch (ControllerException e) {
            e.printStackTrace();
        }

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(Places.GEO_DATA_API)
                    .addApi(Places.PLACE_DETECTION_API)
                    .enableAutoManage(this, this)
                    .build();

        }

        myReceiver = new MyReceiver();

        IntentFilter groupUpdateFilter = new IntentFilter();
        groupUpdateFilter.addAction("inz.agents.MobileAgent.GROUP_UPDATE");
        registerReceiver(myReceiver, groupUpdateFilter);

        IntentFilter centerCalculatedFilter = new IntentFilter();
        centerCalculatedFilter.addAction("inz.agents.MobileAgent.CENTER_CALCULATED");
        registerReceiver(myReceiver, centerCalculatedFilter);

        IntentFilter centerUpdatedFilter = new IntentFilter();
        centerUpdatedFilter.addAction("inz.agents.MobileAgent.CENTER_UPDATED");
        registerReceiver(myReceiver, centerUpdatedFilter);

        IntentFilter stateChangedFilter = new IntentFilter();
        stateChangedFilter.addAction("inz.agents.MobileAgent.STATE_CHANGED");
        registerReceiver(myReceiver, stateChangedFilter);

        IntentFilter placesUpdatedFilter = new IntentFilter();
        placesUpdatedFilter.addAction("inz.agents.MobileAgent.PLACES_UPDATED");
        registerReceiver(myReceiver, placesUpdatedFilter);

        IntentFilter votesUpdatedFilter = new IntentFilter();
        votesUpdatedFilter.addAction("inz.agents.MobileAgent.VOTES_UPDATED");
        registerReceiver(myReceiver, votesUpdatedFilter);

        IntentFilter destChosenFilter = new IntentFilter();
        destChosenFilter.addAction("inz.agents.MobileAgent.DEST_CHOSEN");
        registerReceiver(myReceiver, destChosenFilter);

        mGroupMarkerMap = new HashMap<String, Marker>() {};
        mPlacesMarkerMap = new HashMap<String, Marker>() {};
        mCenterMarker = null;

        findViewById(R.id.button_stage).setVisibility(View.INVISIBLE);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setMapToolbarEnabled(false);

        if (mCurrentLocation != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(mCurrentLocation.getLatitude(),
                                mCurrentLocation.getLongitude()), 10.0f));
        }
        mMap.setOnMarkerDragListener(this);
        mMap.setOnMarkerClickListener(this);
    }

    public void onSettingsClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private Activity getActivity() {
        return this;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if(!mPlacesMarkerMap.isEmpty() && mPlacesMarkerMap.containsKey(marker.getTitle()))
            mCurrSelectedPlace = marker;
        return false;
    }

    private class onAddLocationClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
            builder.setLatLngBounds(new LatLngBounds(
                    new LatLng(mCenterMarker.getPosition().latitude - 0.005,mCenterMarker.getPosition().longitude - 0.005),
                    new LatLng(mCenterMarker.getPosition().latitude + 0.005,mCenterMarker.getPosition().longitude + 0.005)));

            try {
                startActivityForResult(builder.build(getActivity()), PLACE_PICKER);
            } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }
        }
    }

    private class onVoteClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if(mCurrSelectedPlace != null) {
                agentInterface.addVote(mCurrSelectedPlace.getTitle());
            }
        }
    }

    private class onSelectClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            agentInterface.choosePlace(mCurrSelectedPlace.getTitle());
            agentInterface.changeState(MobileAgentInterface.State.LEAD);
        }
    }

    private class onVotingClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            agentInterface.changeState(MobileAgentInterface.State.VOTE);
            Button utilButton = (Button)findViewById(R.id.button_util);
            utilButton.setText("Vote");
            utilButton.setOnClickListener(new onVoteClickListener());

            Button nextStageButton = (Button)findViewById(R.id.button_stage);
            nextStageButton.setText("Select place");
            nextStageButton.setOnClickListener(new onSelectClickListener());
        }
    }

    public void onStartClick(View view) {
        mCenterDraggable = false;
        agentInterface.changeState(MobileAgentInterface.State.CHOOSE);
        mCenterMarker.setDraggable(false);
        Button utilButton = (Button)findViewById(R.id.button_util);
        utilButton.setText("Add location");
        utilButton.setOnClickListener(new onAddLocationClickListener());
        utilButton.setVisibility(View.VISIBLE);

        Button nextStageButton = (Button)findViewById(R.id.button_stage);
        nextStageButton.setText("Voting");
        nextStageButton.setOnClickListener(new onVotingClickListener());

    }

    @Override
    public void onMarkerDrag(Marker marker) {}

    @Override
    public void onMarkerDragEnd(Marker marker) {
        String title = mCenterMarker.getTitle();
        mCenterMarker.remove();
        mCenterMarker = mMap.addMarker(new MarkerOptions().position(marker.getPosition()).title(title).draggable(mCenterDraggable));
        agentInterface.setCenter(marker.getPosition());
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER) {
            if (resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(this, data);
                mPlacesMarkerMap.put((String) place.getName(),
                                    mMap.addMarker(new MarkerOptions().position(place.getLatLng()).title((String) place.getName())));
                agentInterface.addPlace((String) place.getName(), place.getLatLng());
            }
        }
    }

    @Override
    public void onMarkerDragStart(Marker marker) {}

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates(mLocationRequest);
        }
        else
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            //Log.println(Log.ERROR, "Location", "Location permission not granted!");
        }


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates(mLocationRequest);
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    Intent intent = new Intent(this, Menu.class);
                    startActivity(intent);
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }


    protected void startLocationUpdates(LocationRequest mLocationRequest) {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            agentInterface.updateLocation(mCurrentLocation);
            agentInterface.startLocationBroadcast();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
           // Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
           // mGoogleApiClient);

            mCurrentLocation = location;
//            mMap.moveCamera(CameraUpdateFactory.newLatLng(
//                    new LatLng(mCurrentLocation.getLatitude(),
//                            mCurrentLocation.getLongitude())));
            agentInterface.updateLocation(mCurrentLocation);

            //updateMarkers();
            //LatLng here = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            //mMap.addMarker(new MarkerOptions().position(here).title("Actual location"));
            //mMap.moveCamera(CameraUpdateFactory.newLatLng(here));
        }
    }

    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("inz.agents.MobileAgent.GROUP_UPDATE")) {
                ArrayList<AgentPos> group = agentInterface.getGroup();
                LatLng ll;
                for (AgentPos aGroup : group) {
                    if (aGroup.getLatLng() != null) {
                        ll = new LatLng(aGroup.getLatLng().latitude, aGroup.getLatLng().longitude);
                        if(mGroupMarkerMap.containsKey(aGroup.getName()))
                            mGroupMarkerMap.get(aGroup.getName()).remove();

                        mGroupMarkerMap.put(aGroup.getName(),
                                       mMap.addMarker(
                                               new MarkerOptions().
                                                       position(ll).
                                                       title(aGroup.getName())
                                                       .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker))));
                    }
                }
            }
            else if(intent.getAction().equals("inz.agents.MobileAgent.CENTER_CALCULATED")) {
                AgentPos center = agentInterface.getCenter();
                if(center != null) {
                    if(mCenterMarker != null)
                        mCenterMarker.remove();
                    LatLng ll = new LatLng(center.getLatLng().latitude, center.getLatLng().longitude);
                    mCenterMarker = mMap.addMarker(new MarkerOptions().position(ll).title(center.getName()).draggable(mCenterDraggable));
                    if(findViewById(R.id.button_stage).getVisibility() == View.INVISIBLE)
                        findViewById(R.id.button_stage).setVisibility(View.VISIBLE);
                }
            }
            else if(intent.getAction().equals("inz.agents.MobileAgent.CENTER_UPDATED")) {
                AgentPos center = agentInterface.getCenter();
                if(mCenterMarker != null)
                    mCenterMarker.remove();
                LatLng ll = new LatLng(center.getLatLng().latitude, center.getLatLng().longitude);
                mCenterMarker = mMap.addMarker(new MarkerOptions().position(ll).title(center.getName()));
            }
            else if(intent.getAction().equals("inz.agents.MobileAgent.STATE_CHANGED")) {
                MobileAgentInterface.State newState = (MobileAgentInterface.State) intent.getSerializableExtra("State");
                if(newState == MobileAgentInterface.State.CHOOSE ){
                    Button utilButton = (Button)findViewById(R.id.button_util);
                    utilButton.setText("Add location");
                    utilButton.setOnClickListener(new onAddLocationClickListener());
                    utilButton.setVisibility(View.VISIBLE);
                }
                else if(newState == MobileAgentInterface.State.VOTE) {
                    agentInterface.changeState(MobileAgentInterface.State.VOTE);
                    Button utilButton = (Button)findViewById(R.id.button_util);
                    utilButton.setText("Vote");
                    utilButton.setOnClickListener(new onVoteClickListener());
                }
            }
            else if(intent.getAction().equals("inz.agents.MobileAgent.PLACES_UPDATED")) {
                ArrayList<AgentPos> places = agentInterface.getPlaces();
                LatLng ll;
                for (AgentPos aGroup : places) {
                    if (aGroup.getLatLng() != null) {
                        ll = new LatLng(aGroup.getLatLng().latitude, aGroup.getLatLng().longitude);
                        if(mPlacesMarkerMap.containsKey(aGroup.getName()))
                            mPlacesMarkerMap.get(aGroup.getName()).remove();

                        mPlacesMarkerMap.put(aGroup.getName(),
                                mMap.addMarker(
                                        new MarkerOptions().
                                                position(ll).
                                                title(aGroup.getName())));
                    }
                }
            }
            else if(intent.getAction().equals("inz.agents.MobileAgent.VOTES_UPDATED")) {
                HashMap<String, Integer> votes = agentInterface.getVotes();
                for(Map.Entry<String, Integer> entry: votes.entrySet()) {
                    mPlacesMarkerMap.get(entry.getKey()).setSnippet(entry.getValue().toString());
                }
            }
            else if(intent.getAction().equals("inz.agents.MobileAgent.DEST_CHOSEN")) {
                AgentPos dest = agentInterface.getDestination();
                for(Map.Entry<String, Marker> entry: mPlacesMarkerMap.entrySet())
                    entry.getValue().remove();
                mCenterMarker.remove();
                LatLng ll = new LatLng(dest.getLatLng().latitude, dest.getLatLng().longitude);
                mDestMarker = mMap.addMarker(new MarkerOptions().title(dest.getName()).position(ll));
            }

        }
    }
}
