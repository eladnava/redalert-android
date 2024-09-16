package com.red.alert.services.location;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.red.alert.R;
import com.red.alert.activities.settings.alerts.LocationAlerts;
import com.red.alert.config.Logging;
import com.red.alert.config.NotificationChannels;
import com.red.alert.logic.communication.broadcasts.LocationAlertsEvents;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;

import androidx.core.app.NotificationCompat;
import me.pushy.sdk.config.PushyForegroundService;

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

        // Check if all prerequisites are met
        if (!LocationLogic.canStartForegroundLocationService(this)) {
            stopSelf();
            return;
        }

        // Start foreground service
        // API level 34 support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(PushyForegroundService.FOREGROUND_NOTIFICATION_ID, getForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            }
            catch (Exception exc) {
                // Android 14 may still occasionally throw a
                // SecurityException or ForegroundServiceStartNotAllowedException
                // Even throw we have all required permissions
                // And we're starting the service from an allowed place
                stopSelf();
                return;
            }
        } else {
            startForeground(PushyForegroundService.FOREGROUND_NOTIFICATION_ID, getForegroundNotification());
        }

        // Initialize location polling
        initializeLocationPolling();

        // Initialize binder
        mServiceBinder = new LocalBinder();
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

        // Listen for GPS sensor enable/disable event
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_PROVIDER_CHANGED);

        // API level 34 support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(locationProvidersChangedReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(locationProvidersChangedReceiver, filter);
        }

        // Write to log
        Log.d(Logging.TAG, "LocationService started, polling every " + LocationLogic.getUpdateIntervalMilliseconds(this) / 1000 / 60 + " minute(s)");
    }

    public void updateLocationServiceParams() {
        // Called when not requesting?
        if (mLocationRequest == null) {
            return;
        }

        // Set new polling interval
        mLocationRequest.setInterval(LocationLogic.getUpdateIntervalMilliseconds(this));

        // Re-generate notification in case max distance was changed
        updateForegroundServiceNotification();

        // Write to log
        Log.d(Logging.TAG, "LocationService updated, polling every " + LocationLogic.getUpdateIntervalMilliseconds(this) / 1000 / 60 + " minute(s)");
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
        // Check if all prerequisites are met
        if (!LocationLogic.canStartForegroundLocationService(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start foreground service
        // API level 34 support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(PushyForegroundService.FOREGROUND_NOTIFICATION_ID, getForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            }
            catch (Exception exc) {
                // Android 14 may still occasionally throw a
                // SecurityException or ForegroundServiceStartNotAllowedException
                // Even throw we have all required permissions
                // And we're starting the service from an allowed place
                stopSelf();
                return START_NOT_STICKY;
            }
        } else {
            startForeground(PushyForegroundService.FOREGROUND_NOTIFICATION_ID, getForegroundNotification());
        }

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
            } catch (Exception exc) {
                // Log to logcat
                Log.e(Logging.TAG, "LocationClient disconnect failed", exc);
            }
        }

        try {
            // Unregister GPS state change receiver
            unregisterReceiver(locationProvidersChangedReceiver);
        } catch (Exception e) {
            // Ignore exceptions
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

        // Write to log
        Log.d(Logging.TAG, "LocationService stopped");

        // Now we're ready to be destroyed
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        // Get latitude & longitude as float
        float latitude = (float) location.getLatitude();
        float longitude = (float) location.getLongitude();

        // Save to preferences
        LocationLogic.saveLastKnownLocation(this, latitude, longitude);

        // Reload nearby cities in case we're in the Location Alerts settings page
        Broadcasts.publish(this, LocationAlertsEvents.LOCATION_RECEIVED);

        // Write to log
        Log.d(Logging.TAG, "Location: " + latitude + ", " + longitude);

        // Update foreground service notification
        updateForegroundServiceNotification();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected(Bundle dataBundle) {
        // No client?
        if (mClient == null || mLocationRequest == null) {
            Log.e(Logging.TAG, "Location API client or request is null");
            return;
        }

        // One last permission check
        if (!LocationLogic.isLocationAccessGranted(this)) {
            Log.e(Logging.TAG, "Location access not granted");
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

    private BroadcastReceiver locationProvidersChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                // Re-generate notification as GPS may have been disabled or enabled
                updateForegroundServiceNotification();
            }
        }
    };

    @Override
    public void onConnectionSuspended(int i) {
        // Log error
        Log.e(Logging.TAG, "Location API connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // Log error
        Log.e(Logging.TAG, "Location API connection failed: " + connectionResult.getErrorMessage());

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

    void updateForegroundServiceNotification() {
        // Get an instance of the NotificationManager service
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Refresh the notification
        notificationManager.notify(PushyForegroundService.FOREGROUND_NOTIFICATION_ID, getForegroundNotification());
    }

    Notification getForegroundNotification() {
        // Ensure the right language is displayed
        Localization.overridePhoneLocale(this);

        // Location alerts activity pending intent
        PendingIntent launcherIntent = PendingIntent.getActivity(this, 0, new Intent(this, LocationAlerts.class), PendingIntent.FLAG_IMMUTABLE);

        // Build notification title & desc
        String title = getString(R.string.locationAlerts);
        String text;

        // Get current location
        Location location = LocationLogic.getCurrentLocation(this);

        // Location permission revoked?
        if (!LocationLogic.isLocationAccessGranted(this)) {
            // Show error
            text = getString(R.string.enableGPSDesc);
        }
        
        // No recent location?
        else if (location == null) {
            // Show error
            text = getString(R.string.noLocation);
        }
        else {
            // Get nearby cities
            String nearbyCities = LocationData.getNearbyCityNames(location, this);

            // At least one city?
            if (!StringUtils.stringIsNullOrEmpty(nearbyCities)) {
                text = getString(R.string.nearbyCities) + ": " + nearbyCities;

                // Write to log
                Log.d(Logging.TAG, "Nearby cities: " + nearbyCities);
            }
            else {
                // Show error message
                text = getString(R.string.noNearbyCities);

                // Write to log
                Log.d(Logging.TAG, "No nearby cities");
            }
        }

        // Android O and newer requires notification channel to be created prior to dispatching a notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel
            NotificationChannel channel = new NotificationChannel(NotificationChannels.LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_ID, NotificationChannels.LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);

            // Some devices may need us to explicitly disable sound, even for IMPORTANCE_LOW
            channel.setSound(null, null);

            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            // Delete old notification channel
            notificationManager.deleteNotificationChannel(NotificationChannels.LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_OLD_ID);
        }

        // Create foreground notification
        Notification notification = new NotificationCompat.Builder(this, NotificationChannels.LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_ID)
                .setGroup(NotificationChannels.LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_location_service)
                .setColor(Color.TRANSPARENT)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentIntent(launcherIntent)
                .setSound(null)
                .build();

        // All done
        return notification;
    }
}
