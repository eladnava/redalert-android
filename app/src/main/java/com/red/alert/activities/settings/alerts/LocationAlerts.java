package com.red.alert.activities.settings.alerts;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.logic.communication.broadcasts.LocationAlertsEvents;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.services.location.LocationService;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.dialogs.custom.LocationDialogs;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.integration.GooglePlayServices;
import com.red.alert.utils.metadata.LocationData;

public class LocationAlerts extends AppCompatPreferenceActivity
{
    Preference mNearbyCities;

    CheckBoxPreference mGPS;
    SliderPreference mFrequency;
    SliderPreference mMaxDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Must have Google Play Services for this to work
        verifyGooglePlayServicesAvailable();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    private void verifyGooglePlayServicesAvailable()
    {
        // Show dialog
        if ( !GooglePlayServices.isAvailable(this) )
        {
            // Show error
            AlertDialogBuilder.showGenericDialog(getString(R.string.error), getString(R.string.noGooglePlayServices), this, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // No go.
                    finish();
                }
            });
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Ask user to enable GPS in case it's disabled
        LocationDialogs.requestEnableLocationServices(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener()
    {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key)
        {
            // Got new location?
            if (Key.equalsIgnoreCase(LocationAlertsEvents.LOCATION_RECEIVED))
            {
                // Refresh setting summaries with new values
                refreshSummaries();
            }
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    private void initializeUI()
    {
        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Load settings from XML There is no non-deprecated way to do it on API Level 7
        addPreferencesFromResource(R.xml.settings_location_alerts);

        // Cache resource IDs
        mGPS = (CheckBoxPreference) findPreference(getString(R.string.locationAlertsPref));
        mFrequency = (SliderPreference) findPreference(getString(R.string.gpsFrequencyPref));
        mMaxDistance = (SliderPreference) findPreference(getString(R.string.maxDistancePref));
        mNearbyCities = findPreference(getString(R.string.nearbyCitiesPref));

        // Set initial value
        refreshSummaries();

        // Set up listeners
        initializeListeners();
    }

    private void initializeListeners()
    {
        // Max distance changed
        mMaxDistance.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value)
            {
                // Wait until value is saved
                new Handler().postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // Set initial value
                        refreshSummaries();
                    }
                }, 200);

                // Save value
                return true;
            }
        });

        // Location alerts toggled
        mGPS.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value)
            {
                // Wait until value is saved
                new Handler().postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // Can we request location?
                        if (!LocationLogic.shouldRequestLocationUpdates(LocationAlerts.this))
                        {
                            return;
                        }

                        // Ask user to enable GPS
                        LocationDialogs.requestEnableLocationServices(LocationAlerts.this);

                        // Start the location service
                        startService(new Intent(LocationAlerts.this, LocationService.class));
                    }
                }, 200);

                // Save value
                return true;
            }
        });

        // Max distance changed
        mMaxDistance.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener()
        {
            @Override
            public String getDialogMessage(float currentValue)
            {
                // Generate a new summary with the selected value
                return getMaxDistanceSummary(currentValue);
            }
        });

        // Frequency changed - update dialog text
        mFrequency.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener()
        {
            @Override
            public String getDialogMessage(float value)
            {
                // Generate a new summary with the selected value
                return getFrequencySummary(value);
            }
        });

        // Frequency saved
        mFrequency.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value)
            {
                // Wait until value is saved
                new Handler().postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // Set initial value
                        refreshSummaries();

                        // Update location service's polling interval
                        applyLocationFrequency();
                    }
                }, 200);

                // Save value
                return true;
            }
        });
    }

    public void applyLocationFrequency()
    {
        // Bind to our location service
        bindService(new Intent(this, LocationService.class), new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder)
            {
                // Convert binder to LocalBinder
                LocationService.LocalBinder localBinder = (LocationService.LocalBinder) binder;

                // Get service instance
                LocationService service = localBinder.getService();

                // Re-apply interval
                service.updateRequestInterval();

                // Unbind fom service
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName)
            {
                // Do nothing
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private String getFrequencySummary(float OverrideValue)
    {
        // Construct summary text
        return getString(R.string.gpsFrequencyDesc) + "\r\n(" + getString(R.string.every) + " " + LocationLogic.getUpdateIntervalMinutes(this, OverrideValue) + " " + getString(R.string.minutes) + ")";
    }

    private String getMaxDistanceSummary(float OverrideValue)
    {
        // Construct summary text
        return getString(R.string.maxDistanceDesc) + "\r\n(" + LocationLogic.getMaxDistanceKilometers(this, OverrideValue) + " " + getString(R.string.kilometer) + ")";
    }

    private void refreshSummaries()
    {
        // Update summary text
        mMaxDistance.setSummary(getMaxDistanceSummary(-1));

        // Update summary text
        mFrequency.setSummary(getFrequencySummary(-1));

        // Explode into array
        Location location = LocationLogic.getLocation(this);

        // No recent location?
        String nearby;

        // No recent location?
        if (location == null)
        {
            // Show error
            nearby = getString(R.string.noLocation);
        }
        else
        {
            // Get nearby cities
            nearby = LocationData.getNearbyCityNames(location, this);

            // No results?
            if (StringUtils.stringIsNullOrEmpty(nearby))
            {
                // Show error
                nearby = getString(R.string.noNearbyCities);
            }
        }

        // Update summary text
        mNearbyCities.setSummary(nearby);
    }

    public boolean onOptionsItemSelected(final MenuItem Item)
    {
        // Check item ID
        switch (Item.getItemId())
        {
            // Home button?
            case android.R.id.home:
                onBackPressed();
        }

        return super.onOptionsItemSelected(Item);
    }
}
