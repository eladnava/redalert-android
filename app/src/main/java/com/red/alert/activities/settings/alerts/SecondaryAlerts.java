package com.red.alert.activities.settings.alerts;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.metadata.LocationData;

public class SecondaryAlerts extends AppCompatPreferenceActivity
{
    SliderPreference mSecondaryVolume;
    SearchableMultiSelectPreference mSecondaryCitySelection;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
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

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);
    }

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener()
    {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key)
        {
            // Asked for reload?
            if (Key.equalsIgnoreCase(LocationSelectionEvents.REFRESH_AREA_VALUES))
            {
                // Reload our summaries
                refreshAreaValues();
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

    void initializeUI()
    {
        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Load settings from XML There is no non-deprecated way to do it on API Level 7
        addPreferencesFromResource(R.xml.settings_secondary_alerts);

        // Cache resource IDs
        mSecondaryVolume = ((SliderPreference) findPreference(getString(R.string.secondaryVolumePref)));
        mSecondaryCitySelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedSecondaryCitiesPref)));

        // Populate setting values
        initializeSettings();

        // Set up listeners
        initializeListeners();
    }

    void initializeSettings()
    {
        // Set entries & values
        mSecondaryCitySelection.setEntries(LocationData.getAllCityNames(this));
        mSecondaryCitySelection.setEntryValues(LocationData.getAllCityValues(this));

        // Refresh area values
        refreshAreaValues();
    }

    void refreshAreaValues()
    {
        // Get secondary cities
        String secondaryCities = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedSecondaryCitiesPref), getString(R.string.all));

        // Update summary text
        mSecondaryCitySelection.setSummary(getString(R.string.selectedSecondaryCitiesDesc) + "\r\n(" + LocationData.getSelectedCityNamesByValues(this, secondaryCities, mSecondaryCitySelection.getEntries(), mSecondaryCitySelection.getEntryValues()) + ")");
    }

    void initializeListeners()
    {
        // Volume selection
        mSecondaryVolume.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener()
        {
            @Override
            public String getDialogMessage(float Value)
            {
                // Get slider percent
                int percent = (int) (AppPreferences.getSecondaryAlertVolume(SecondaryAlerts.this, Value) * 100);

                // Generate summary
                return getString(R.string.secondaryVolumeDesc) + "\r\n(" + percent + "%)";
            }
        });
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
