package com.red.alert.activities.settings;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;

import androidx.preference.TwoStatePreference;
import androidx.preference.Preference;

import com.red.alert.R;
import com.red.alert.activities.Map;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.services.ServiceManager;
import com.red.alert.model.Alert;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.metadata.LocationData;

import java.util.ArrayList;
import java.util.List;

public class LocationAlertsFragment extends BasePreferenceFragment {
    TwoStatePreference mLocationAlerts;
    SliderPreference mMaxDistance;
    SliderPreference mFrequency;
    Preference mNearbyCities;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_location_alerts, rootKey);

        // Cache preferences
        mLocationAlerts = findPreference(getString(R.string.locationAlertsPref));
        mMaxDistance = findPreference(getString(R.string.maxDistancePref));
        mFrequency = findPreference(getString(R.string.gpsFrequencyPref));
        mNearbyCities = findPreference(getString(R.string.nearbyCitiesPref));

        // Set up listeners
        initializeListeners();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Location alerts enabled and permission not granted?
        if (mLocationAlerts != null && mLocationAlerts.isChecked() && !LocationLogic.isLocationAccessGranted(getActivity())) {
            // Disable location alerts
            mLocationAlerts.setChecked(false);

            // Stop the location service
            ServiceManager.stopLocationService(getActivity());

            // Request permission via dialog
            LocationLogic.showLocationAccessRequestDialog(getActivity());
        }
    }

    void updateLocationService() {
        // Location alerts enabled?
        if (mLocationAlerts != null && mLocationAlerts.isChecked() && getActivity() != null) {
            // Restart the location service with the new settings
            ServiceManager.stopLocationService(getActivity());
            ServiceManager.startLocationService(getActivity());
        }
    }

    void initializeListeners() {
        // Location alerts toggle
        if (mLocationAlerts != null) {
            mLocationAlerts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // Trying to enable location-based alerts?
                    if ((boolean) newValue) {
                        // Can we access the user's location?
                        if (!LocationLogic.isLocationAccessGranted(getActivity())) {
                            // Request permission via dialog
                            LocationLogic.showLocationAccessRequestDialog(getActivity());
                            // Don't enable location alerts yet
                            return false;
                        }
                        // Start the location service
                        ServiceManager.startLocationService(getActivity());
                    } else {
                        // Stop the location service
                        ServiceManager.stopLocationService(getActivity());
                    }
                    // Save new value
                    return true;
                }
            });
        }

        // Max distance change listener
        if (mMaxDistance != null) {
            mMaxDistance.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    // Wait until value is saved then update service
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateLocationService();
                        }
                    }, 200);
                    return true;
                }
            });
        }

        // Frequency change listener
        if (mFrequency != null) {
            mFrequency.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    // Wait until value is saved then update service
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateLocationService();
                        }
                    }, 200);
                    return true;
                }
            });
        }

        // Nearby cities click listener
        if (mNearbyCities != null) {
            mNearbyCities.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) return false;

                    // Get current location
                    Location location = LocationLogic.getCurrentLocation(getActivity());

                    // No location?
                    if (location == null) {
                        return false;
                    }

                    // Get nearby cities
                    List<String> nearbyCities = LocationData.getNearbyCities(location, getActivity());

                    // Prepare mock alerts list for map activity
                    List<Alert> mockAlerts = new ArrayList<>();

                    // Traverse nearby cities
                    for (String city : nearbyCities) {
                        // Create mock alert object for map display
                        Alert alert = new Alert();
                        alert.city = city;
                        alert.date = DateTime.getUnixTimestamp();
                        alert.threat = ThreatTypes.NEARBY_CITIES_DISPLAY;
                        mockAlerts.add(alert);
                    }

                    // Create new intent
                    Intent map = new Intent();
                    map.setClass(getActivity(), Map.class);

                    // Pass alerts to map
                    Map.mAlerts = mockAlerts;

                    // Start map activity
                    startActivity(map);

                    return true;
                }
            });
        }
    }
}