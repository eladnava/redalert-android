package com.red.alert.logic.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;

import com.red.alert.R;
import com.red.alert.config.LocationAlerts;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.utils.caching.Singleton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class LocationLogic {
    public static int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    public static int getUpdateIntervalMinutes(Context context, float overrideSetting) {
        // Get stored value
        float sliderValue = Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.gpsFrequencyPref), Float.parseFloat(context.getString(R.string.defaultGPSPollingFrequency)));

        // Override it?
        if (overrideSetting != -1) {
            sliderValue = overrideSetting;
        }

        // Calculate frequency in minutes
        int frequencyMin = (int) (LocationAlerts.MAX_FREQUENCY_MINUTES * sliderValue);

        // Return it
        return frequencyMin;
    }

    public static int getMaxDistanceKilometers(Context context, float overrideSetting) {
        // Get stored value
        float sliderValue = Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.maxDistancePref), Float.parseFloat(context.getString(R.string.defaultGPSMaxDistance)));

        // Override it?
        if (overrideSetting != -1) {
            sliderValue = overrideSetting;
        }

        // Calculate frequency in minutes
        int maxDistance = (int) (LocationAlerts.MAX_DISTANCE_KM * sliderValue);

        // Return it
        return maxDistance;
    }

    public static long getUpdateIntervalMilliseconds(Context context) {
        // Convert to milliseconds
        return getUpdateIntervalMinutes(context, -1) * 60 * 1000;
    }

    public static void saveLastKnownLocation(Context context, float latitude, float longitude) {
        // Get shared preferences
        SharedPreferences preferences = Singleton.getSharedPreferences(context);

        // Edit preferences
        android.content.SharedPreferences.Editor editor = preferences.edit();

        // Store latitude & longitude
        editor.putFloat(context.getString(R.string.latitudePref), latitude);
        editor.putFloat(context.getString(R.string.longitudePref), longitude);

        // Commit changes
        editor.commit();
    }

    public static Location getCurrentLocation(Context context) {
        // Get shared preferences
        SharedPreferences preferences = Singleton.getSharedPreferences(context);

        // Load saved latitude and longitude from SharedPreferences
        double latitude = preferences.getFloat(context.getString(R.string.latitudePref), 0);
        double longitude = preferences.getFloat(context.getString(R.string.longitudePref), 0);

        // No location stored?
        if (latitude == 0) {
            return GetLastKnownLocation(context);
        }

        // Prepare new location object
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);

        // Set latitude & longitude
        location.setLatitude(latitude);
        location.setLongitude(longitude);

        // Return it
        return location;
    }

    @SuppressLint("MissingPermission")
    public static Location GetLastKnownLocation(Context context) {
        // No permission?
        if (!isLocationAccessGranted(context)) {
            return null;
        }

        // Get location manager
        LocationManager locationManager = (LocationManager) context.getSystemService(context.LOCATION_SERVICE);

        // Create location criteria
        Criteria criteria = new Criteria();

        // Set coarse accuracy
        criteria.setAccuracy(criteria.ACCURACY_COARSE);

        // Get best provider
        String bestProvider = locationManager.getBestProvider(criteria, false);

        // No provider?
        if (bestProvider == null) {
            // Return null
            return null;
        }

        // Return last location
        return locationManager.getLastKnownLocation(bestProvider);
    }

    public static boolean isGPSEnabled(Context context) {
        // Get location manager
        LocationManager locationManager = (LocationManager) context.getSystemService(context.LOCATION_SERVICE);

        // GPS enabled?
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public static boolean isLocationPermissionGranted(Context context) {
        // Check if location permissions are granted
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    public static void requestLocationPermission(Activity context) {
        // Display location permission dialog
        ActivityCompat.requestPermissions(context,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    public static boolean isLocationAccessGranted(Context context) {
        // Check if GPS sensor enabled and location permissions are granted (Android 10+)
        return isGPSEnabled(context) && isLocationPermissionGranted(context);
    }

    public static void showLocationAccessRequestDialog(final Activity context) {
        // GPS sensor disabled?
        if (!isGPSEnabled(context)) {
            // Show a dialog to the user
            AlertDialogBuilder.showGenericDialog(context.getString(R.string.locationAlerts), context.getString(R.string.enableGPSDesc), context.getString(R.string.okay), context.getString(R.string.notNow), true, context, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    // Clicked okay?
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        // Display the GPS setting screen
                        context.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                }
            });
        }

        // Check if location permissions need to be granted
        else if (!isLocationPermissionGranted(context)) {
            // Request location permission
            requestLocationPermission(context);
        }
    }
}
