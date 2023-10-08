package com.red.alert.activities.settings;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.Preference;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.feedback.Volume;

public class Advanced extends AppCompatPreferenceActivity {
    Preference mSecondaryAlerts;

    SliderPreference mVolumeSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI() {
        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Load settings from XML There is no non-deprecated way to do it on API Level 7
        addPreferencesFromResource(R.xml.settings_advanced);

        // Cache resource IDs
        mSecondaryAlerts = findPreference(getString(R.string.secondaryPref));
        mVolumeSelection = (SliderPreference) findPreference(getString(R.string.volumePref));

        // Set up listeners
        initializeListeners();
    }

    void initializeListeners() {
        // Volume selection
        mVolumeSelection.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener() {
            @Override
            public String getDialogMessage(float Value) {
                // Get slider percent
                int percent = (int) (AppPreferences.getPrimaryAlertVolume(Advanced.this, Value) * 100);

                // Generate summary
                return getString(R.string.volumeDesc) + "\r\n(" + percent + "%)";
            }
        });

        // Set up secondary alerts click listener
        mSecondaryAlerts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent secondaryAlerts = new Intent();

                // Set class
                secondaryAlerts.setClass(Advanced.this, SecondaryAlerts.class);

                // Show settings
                startActivity(secondaryAlerts);

                // Consume event
                return true;
            }
        });
    }

    public boolean onOptionsItemSelected(final MenuItem Item) {
        // Check item ID
        switch (Item.getItemId()) {
            // Home button?
            case android.R.id.home:
                onBackPressed();
        }

        return super.onOptionsItemSelected(Item);
    }
}
