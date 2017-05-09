package inz.maptest;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Arrays;

import inz.agents.MobileAgentInterface;
import inz.util.AgentPos;
import inz.util.ParcelableLatLng;
import jade.core.MicroRuntime;
import jade.wrapper.ControllerException;

import static inz.maptest.R.id.map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;

    private MyReceiver myReceiver;

    private String nickname;
    private MobileAgentInterface agentInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        nickname = getIntent().getStringExtra("AGENT_NICKNAME");

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
                    .build();
        }

        myReceiver = new MyReceiver();

        IntentFilter groupUpdateFilter = new IntentFilter();
        groupUpdateFilter.addAction("inz.agents.MobileAgent.GROUP_UPDATE");
        registerReceiver(myReceiver, groupUpdateFilter);

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
        mMap.addMarker(new MarkerOptions().position(new LatLng(52.26, 21.0)).title("Chosen location").icon(BitmapDescriptorFactory.fromResource(R.drawable.marker)));

        // Add a marker in Sydney and move the camera
        //LatLng sydney = new LatLng(-34, 151);
        //mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    public void onSettingsClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

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
                                           String permissions[], int[] grantResults) {
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

            //ArrayList<AgentPos<String, Location>> group = new ArrayList<>(Arrays.asList((AgentPos<String, Location>[]) intent.getSerializableExtra("GROUP")));

            //AgentPos<String, Location>[] group = (AgentPos<String, Location>[]) intent.getSerializableExtra("GROUP");

            //Bundle bundle = getIntent().getExtras();
            ArrayList<AgentPos> group = agentInterface.getGroup();
            LatLng ll;
            mMap.clear();
            for(AgentPos aGroup: group){
                if(aGroup.getLatLng() != null) {
                    ll = new LatLng(aGroup.getLatLng().latitude, aGroup.getLatLng().longitude);
                    mMap.addMarker(new MarkerOptions().position(ll).title(aGroup.getName()).icon(BitmapDescriptorFactory.fromResource(R.drawable.marker)));
                }
            }

        }
    }
}
