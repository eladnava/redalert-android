package com.red.alert.logic.location;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;

import com.red.alert.R;
import com.red.alert.config.LocationAlerts;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.integration.GooglePlayServices;

import androidx.core.app.ActivityCompat;

public class LocationLogic {
    public static int getUpdateIntervalMinutes(Context context, float overrideSetting) {
        // Get stored value
        float sliderValue = Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.gpsFrequencyPref), 0.02f);

        // Override it?
        if (overrideSetting != -1) {
            sliderValue = overrideSetting;
        }

        // Calculate frequency in minutes
        int frequencyMin = (int) (LocationAlerts.MAX_FREQUENCY_MIN * sliderValue);

        // Return it
        return frequencyMin;
    }

    public static int getMaxDistanceKilometers(Context context, float overrideSetting) {
        // Get stored value
        float sliderValue = Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.maxDistancePref), 0.1f);

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

    public static boolean shouldRequestLocationUpdates(Context context) {
        // Must have Google Play Services
        if (!GooglePlayServices.isAvailable(context)) {
            return false;
        }

        // Get location alerts enabled setting
        boolean locationAlerts = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.locationAlertsPref), false);

        // Is it enabled?
        if (!locationAlerts) {
            return false;
        }

        // Interval set to 0?
        if (getUpdateIntervalMilliseconds(context) == 0) {
            return false;
        }

        // We're good
        return true;
    }

    public static void saveLastKnownLocation(Context context, float latitude, float longitude) {
        // Get shared preferences
        SharedPreferences preferences = Singleton.getSharedPreferences(context);

        // Edit preferences
        android.content.SharedPreferences.Editor editor = preferences.edit();

        // Store lat & long
        editor.putFloat(context.getString(R.string.latitudePref), latitude);
        editor.putFloat(context.getString(R.string.longitudePref), longitude);

        // Commit changes
        editor.commit();
    }

    public static Location getLocation(Context context) {
        // Get shared preferences
        android.content.SharedPreferences preferences = Singleton.getSharedPreferences(context);

        // Edit preferences
        double latitude = preferences.getFloat(context.getString(R.string.latitudePref), 0);
        double longitude = preferences.getFloat(context.getString(R.string.longitudePref), 0);

        // No location stored?
        if (latitude == 0) {
            return GetLastKnownLocation(context);
        }

        // Prepare new location object
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);

        // Set lat & long
        location.setLatitude(latitude);
        location.setLongitude(longitude);

        // Return it
        return location;
    }

    public static Location GetLastKnownLocation(Context context) {
        // Get location manager
        LocationManager locationManager = (LocationManager) context.getSystemService(context.LOCATION_SERVICE);

        // Create location criteria
        Criteria criteria = new Criteria();

        // Set fine accuracy
        criteria.setAccuracy(criteria.ACCURACY_FINE);

        // Get best provider
        String bestProvider = locationManager.getBestProvider(criteria, false);

        // No provider?
        if (bestProvider == null) {
            // Return null
            return null;
        }

        // No permission?
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        // Return last location
        return locationManager.getLastKnownLocation(bestProvider);
    }
}
