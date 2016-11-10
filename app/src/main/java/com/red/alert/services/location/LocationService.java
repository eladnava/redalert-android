package com.red.alert.services.location;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.red.alert.config.Logging;
import com.red.alert.logic.communication.broadcasts.LocationAlertsEvents;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.integration.GooglePlayServices;

public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    IBinder mServiceBinder;
    GoogleApiClient mClient;
    LocationRequest mLocationRequest;

    @Override
    public void onCreate() {
        super.onCreate();

        // Must have Google Play Services
        if (!GooglePlayServices.isAvailable(this)) {
            return;
        }

        // Initialize location polling
        initializeLocationPolling();

        // Initialize binder
        mServiceBinder = new LocalBinder();

        // Write to log
        Log.d(Logging.TAG, "LocationService started");
    }

    void initializeLocationPolling() {
        // Create new request
        mLocationRequest = LocationRequest.create();

        // Low battery mode
        mLocationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);

        // Set update intervals
        mLocationRequest.setInterval(LocationLogic.getUpdateIntervalMilliseconds(this));

        // Create new location receive client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    public void updateRequestInterval() {
        // Called when not requesting?
        if (mLocationRequest == null) {
            return;
        }

        // Set update intervals
        mLocationRequest.setInterval(LocationLogic.getUpdateIntervalMilliseconds(this));
    }

    void connectLocationClient() {
        // Got a location client?
        if (mClient != null) {
            // Client isn't already connected?
            if (!mClient.isConnected() || !mClient.isConnecting()) {
                // Ask for updates
                mClient.connect();
            }
        }
    }

    @Override
    public int onStartCommand(Intent Intent, int Flags, int StartId) {
        // Try connecting
        connectLocationClient();

        // Sticky service!
        return START_STICKY;
    }

    void disconnectClient() {
        // Got a client?
        if (mClient != null && mClient.isConnected()) {
            try {
                // Stop asking for updates
                LocationServices.FusedLocationApi.removeLocationUpdates(mClient, LocationService.this);

                // Disconnect client
                mClient.disconnect();
            }
            catch (Exception exc) {
                // Log to logcat
                Log.e(Logging.TAG, "LocationClient disconnect failed", exc);
            }
        }
    }

    void reconnect() {
        // First, try disconnecting
        disconnectClient();

        // Connect again
        connectLocationClient();
    }

    @Override
    public void onDestroy() {
        // Got a client?
        disconnectClient();

        // Now we're ready to be destroyed
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        // Get lat & long as float
        float latitude = (float) location.getLatitude();
        float longitude = (float) location.getLongitude();

        // Save to preferences
        LocationLogic.saveLastKnownLocation(this, latitude, longitude);

        // Reload nearby cities in case we're in the Location Alerts settings page
        Broadcasts.publish(this, LocationAlertsEvents.LOCATION_RECEIVED);

        // Write to log
        Log.d(Logging.TAG, "Location: " + latitude + "," + longitude);
    }

    @Override
    public void onConnected(Bundle dataBundle) {
        // No client?
        if (mClient == null || mLocationRequest == null) {
            return;
        }

        try {
            // Request updates
            LocationServices.FusedLocationApi.requestLocationUpdates(mClient, mLocationRequest, this);
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "FusedLocationApi connection failed", exc);

            // Try reconnecting
            reconnect();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Should we do something in this case?
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // Try reconnecting
        reconnect();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Provide service binder
        return mServiceBinder;
    }

    public class LocalBinder extends Binder {
        public LocationService getService() {
            // Return the instance
            return LocationService.this;
        }
    }
}
