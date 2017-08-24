package inz.maptest;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import inz.agents.MobileAgentInterface;
import inz.util.AgentPos;
import inz.util.BitmapResize;
import inz.util.EncodedPolylineDecoder;
import inz.util.PopUpWindow;
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
    private final int DEST_MARKER_BASE_SIZE = 20;
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
    private int votes;

    private List<LatLng> mPoly = new ArrayList();
    private Polyline route = null;
    private String copyrights;

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

        IntentFilter mobileAgentFilter = new IntentFilter();
        mobileAgentFilter.addAction("inz.agents.MobileAgent.GROUP_UPDATE");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.CENTER_CALCULATED");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.CENTER_UPDATED");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.STATE_CHANGED");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.PLACES_UPDATED");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.VOTES_UPDATED");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.DEST_CHOSEN");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.AGENT_LEFT");
        mobileAgentFilter.addAction("inz.agents.MobileAgent.HOST_LEFT");
        registerReceiver(myReceiver, mobileAgentFilter);

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

    protected void doFinish() {
        this.finish();
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
            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }
        }
    }

    private class onVoteClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if(mCurrSelectedPlace != null) {
                if(votes > 0) {
                    votes = votes - 1;
                    if(votes == 0)
                        ((TextView) findViewById(R.id.votesNo)).setText("");
                    else ((TextView) findViewById(R.id.votesNo)).setText("" + votes);
                    agentInterface.addVote(mCurrSelectedPlace.getTitle());
                }
            }
        }
    }

    private class onSelectClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {

            if(mCurrSelectedPlace == null || mCurrSelectedPlace == mCenterMarker)
            {
                new PopUpWindow(v.getContext(), "Select place", "You must select one place to progress");
                return;
            }

            mDestMarker = mCurrSelectedPlace;

            agentInterface.choosePlace(mCurrSelectedPlace.getTitle());
            agentInterface.changeState(MobileAgentInterface.State.LEAD);

            Button utilButton = (Button)findViewById(R.id.button_util);
            utilButton.setVisibility(View.INVISIBLE);

            Button nextStageButton = (Button)findViewById(R.id.button_stage);
            nextStageButton.setVisibility(View.INVISIBLE);

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

            votes = agentInterface.getMaxVotes();

            ((TextView) findViewById(R.id.votesNo)).setText("" + votes);

            ((TextView) findViewById(R.id.copyrights)).setText("VOTE NOW");
            (findViewById(R.id.copyrights)).setVisibility(View.VISIBLE);

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

        ((TextView) findViewById(R.id.copyrights)).setText("ADD LOCATIONS");
        (findViewById(R.id.copyrights)).setVisibility(View.VISIBLE);

    }

    @Override
    public void onMarkerDrag(Marker marker) {}

    @Override
    public void onMarkerDragEnd(Marker marker) {
        String title = mCenterMarker.getTitle();
        mCenterMarker.remove();
        mCenterMarker = mMap.addMarker(
                                new MarkerOptions().position(marker.getPosition()).title(title)
                                        .draggable(mCenterDraggable)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.centermarker))
                                        .anchor(0.5f, 0.5f)
                        );
        agentInterface.setCenter(marker.getPosition());
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER) {
            if (resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(this, data);
                mPlacesMarkerMap.put((String) place.getName(),
                                    mMap.addMarker(new MarkerOptions()
                                            .position(place.getLatLng())
                                            .title((String) place.getName())
                                            .anchor(0.5f, 0.5f)
                                            .icon(BitmapDescriptorFactory.fromBitmap(BitmapResize.resizeMapIcons(this, "destmarker", DEST_MARKER_BASE_SIZE, DEST_MARKER_BASE_SIZE)))// fromResource(R.drawable.destmarker))
                                    ));
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

    protected void googleDirectionsRequest() {
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin="
                +mCurrentLocation.getLatitude()+","+mCurrentLocation.getLongitude()
                +"&destination="+mDestMarker.getPosition().latitude+","+mDestMarker.getPosition().longitude+
                "&key="+getResources().getString(R.string.google_directions_key);
        new googleDirectionsRequestJob().execute(url);
    }

    private class googleDirectionsRequestJob extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String[] params) {
            try {
                URL url = new URL(params[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                try {
                    InputStream error = urlConnection.getErrorStream();
                    if(error != null) {
                        throw new Exception(error.toString());
                    }
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    String responseStr = convertStreamToString(in);
                    //StringReader sr = new StringReader(responseStr);
                    //JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
                    JSONObject responseObj = new JSONObject(responseStr);
                    String status = (String) responseObj.get("status");
                    if(!status.equals("OK"))
                        throw(new Exception("Response status \"" + status + "\""));
                    JSONObject routes = ((JSONArray) responseObj.get("routes")).getJSONObject(0);
                    copyrights = (String) routes.get("copyrights");
                    JSONObject legs = ((JSONArray) routes.getJSONArray("legs")).getJSONObject(0);
                    JSONArray steps = (JSONArray) legs.getJSONArray("steps");
                    String encodedPoints;
                    mPoly.clear();
                    for (int i=0; i<steps.length(); ++i) {
                        encodedPoints = (String) steps.getJSONObject(i).getJSONObject("polyline").get("points");
                        mPoly.addAll(EncodedPolylineDecoder.decodePoly(encodedPoints));
                    }

                } catch(Exception e) {
                    e.printStackTrace();
                } finally {
                    urlConnection.disconnect();
                }
            } catch(java.io.IOException e) {
                e.printStackTrace();
            }
            return "Success";
        }

        @Override
        protected void onPostExecute(String message) {
            //process message
            if(message.equals("Success")) {
                try {
                    ((TextView) findViewById(R.id.copyrights)).setText(copyrights);
                    (findViewById(R.id.copyrights)).setVisibility(View.VISIBLE);
                    if(route!=null)
                        route.remove();
                    route = mMap.addPolyline(new PolylineOptions().addAll(mPoly).width(5).color(Color.BLUE));
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
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
            if(mCurrentLocation != null){
                agentInterface.updateLocation(mCurrentLocation);
                agentInterface.startLocationBroadcast();
            }

        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
           // Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
           // mGoogleApiClient);
            if(mCenterMarker == null) {
                agentInterface.updateLocation(location);
                agentInterface.startLocationBroadcast();
            }
            else{
                agentInterface.updateLocation(location);
            }
            mCurrentLocation = location;


//            mMap.moveCamera(CameraUpdateFactory.newLatLng(
//                    new LatLng(mCurrentLocation.getLatitude(),
//                            mCurrentLocation.getLongitude())));


            if( mDestMarker != null ) {
//                if(route != null)
//                    route.remove();
                googleDirectionsRequest();
            }
        }
    }

    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action.equals("inz.agents.MobileAgent.GROUP_UPDATE")) {
                ArrayList<AgentPos> group = agentInterface.getGroup();
                LatLng ll;
                for(Marker aMarker : mGroupMarkerMap.values())
                    aMarker.remove();
                for (AgentPos aGroup : group) {
                    if (aGroup.getLatLng() != null) {
                        ll = new LatLng(aGroup.getLatLng().latitude, aGroup.getLatLng().longitude);
//                        if(mGroupMarkerMap.containsKey(aGroup.getName()))
//                            mGroupMarkerMap.get(aGroup.getName()).remove();

                        mGroupMarkerMap.put(aGroup.getName(),
                                       mMap.addMarker(
                                               new MarkerOptions().
                                                       position(ll).
                                                       title(aGroup.getName())
                                                       .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker))
                                                       .anchor(0.5f, 0.5f)));
                    }
                }
            }
            else if(action.equals("inz.agents.MobileAgent.CENTER_CALCULATED")) {
                AgentPos center = agentInterface.getCenter();
                if(center != null) {
                    if(mCenterMarker != null)
                        mCenterMarker.remove();
                    LatLng ll = new LatLng(center.getLatLng().latitude, center.getLatLng().longitude);
                    mCenterMarker = mMap.addMarker(new MarkerOptions().position(ll).title(center.getName()).draggable(mCenterDraggable).icon(BitmapDescriptorFactory.fromResource(R.drawable.centermarker)).anchor(0.5f, 0.5f));
                    if(findViewById(R.id.button_stage).getVisibility() == View.INVISIBLE)
                        findViewById(R.id.button_stage).setVisibility(View.VISIBLE);
                }
            }
            else if(action.equals("inz.agents.MobileAgent.CENTER_UPDATED")) {
                AgentPos center = agentInterface.getCenter();
                if(mCenterMarker != null)
                    mCenterMarker.remove();
                LatLng ll = new LatLng(center.getLatLng().latitude, center.getLatLng().longitude);
                mCenterMarker = mMap.addMarker(new MarkerOptions().position(ll).title(center.getName()).icon(BitmapDescriptorFactory.fromResource(R.drawable.centermarker)).anchor(0.5f, 0.5f));
            }
            else if(action.equals("inz.agents.MobileAgent.STATE_CHANGED")) {
                MobileAgentInterface.State newState = (MobileAgentInterface.State) intent.getSerializableExtra("State");
                if(newState == MobileAgentInterface.State.CHOOSE ){
                    Button utilButton = (Button)findViewById(R.id.button_util);
                    utilButton.setText("Add location");
                    utilButton.setOnClickListener(new onAddLocationClickListener());
                    utilButton.setVisibility(View.VISIBLE);

                    ((TextView) findViewById(R.id.copyrights)).setText("ADD LOCATIONS");
                    (findViewById(R.id.copyrights)).setVisibility(View.VISIBLE);
                }
                else if(newState == MobileAgentInterface.State.VOTE) {
                    agentInterface.changeState(MobileAgentInterface.State.VOTE);
                    Button utilButton = (Button)findViewById(R.id.button_util);
                    utilButton.setText("Vote");
                    utilButton.setOnClickListener(new onVoteClickListener());

                    votes = agentInterface.getMaxVotes();
                    ((TextView) findViewById(R.id.votesNo)).setText("" + votes);

                    ((TextView) findViewById(R.id.copyrights)).setText("VOTE NOW");
                    (findViewById(R.id.copyrights)).setVisibility(View.VISIBLE);

                }
            }
            else if(action.equals("inz.agents.MobileAgent.PLACES_UPDATED")) {
                ArrayList<AgentPos> places = agentInterface.getPlaces();
                LatLng ll;
                for (AgentPos aGroup : places) {
                    if (aGroup.getLatLng() != null) {
                        ll = new LatLng(aGroup.getLatLng().latitude, aGroup.getLatLng().longitude);
                        if(mPlacesMarkerMap.containsKey(aGroup.getName()))
                            mPlacesMarkerMap.get(aGroup.getName()).remove();

                        mPlacesMarkerMap.put((String) aGroup.getName(),
                                mMap.addMarker(new MarkerOptions()
                                        .position(ll)
                                        .title((String) aGroup.getName())
                                        .anchor(0.5f, 0.5f)
                                        .icon(BitmapDescriptorFactory.fromBitmap(BitmapResize.resizeMapIcons(context, "destmarker", DEST_MARKER_BASE_SIZE, DEST_MARKER_BASE_SIZE)))// fromResource(R.drawable.destmarker))
                                ));
                    }
                }
            }
            else if(action.equals("inz.agents.MobileAgent.VOTES_UPDATED")) {
                HashMap<String, Integer> votes = agentInterface.getVotes();
                for(Map.Entry<String, Integer> entry: votes.entrySet()) {
                    int enlarge = entry.getValue() * 2;
                    mPlacesMarkerMap.get(entry.getKey()).setIcon(BitmapDescriptorFactory.fromBitmap(BitmapResize.resizeMapIcons(context, "destmarker", DEST_MARKER_BASE_SIZE + enlarge, DEST_MARKER_BASE_SIZE + enlarge)));
                    mPlacesMarkerMap.get(entry.getKey()).setSnippet(entry.getValue().toString());
                }
            }
            else if(action.equals("inz.agents.MobileAgent.DEST_CHOSEN")) {
                AgentPos dest = agentInterface.getDestination();
                for(Map.Entry<String, Marker> entry: mPlacesMarkerMap.entrySet())
                    entry.getValue().remove();
                mCenterMarker.remove();
                LatLng ll = new LatLng(dest.getLatLng().latitude, dest.getLatLng().longitude);
                mDestMarker = mMap.addMarker(new MarkerOptions().title(dest.getName()).position(ll));

                Button utilButton = (Button)findViewById(R.id.button_util);
                utilButton.setVisibility(View.INVISIBLE);

                googleDirectionsRequest();
            }
            else if(action.equals("inz.agents.MobileAgent.AGENT_LEFT"))
                new PopUpWindow(context, "Agent left", intent.getStringExtra("NAME") + " has left the group.");
            else if(action.equals("inz.agents.MobileAgent.HOST_LEFT")) {
                //new PopUpWindow(context, "Host left", "Host has left the group.");
                doFinish();
            }

        }
    }
}
