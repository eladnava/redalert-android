package com.red.alert.activities.settings.integrations;

import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.integration.BluetoothIntegration;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.dialogs.custom.BluetoothDialogs;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.feedback.Volume;

public class DeviceIntegrations extends AppCompatPreferenceActivity
{
    CheckBoxPreference mMiBand;
    Preference mTestIntegrations;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    private void showImportantDialogs()
    {
        // No Bluetooth connectivity in device?
        if ( ! BluetoothIntegration.isBLESupported(this) )
        {
            // Show fatal dialog
            BluetoothDialogs.showBLENotSupportedDialog(this);

            // Avoid duplicate dialog
            return;
        }

        // Bluetooth disabled?
        if ( ! BluetoothIntegration.isBluetoothEnabled() )
        {
            // Ask user to enable bluetooth politely
            BluetoothDialogs.showEnableBluetoothDialog(this);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // Any dialogs to display?
        showImportantDialogs();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

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
        addPreferencesFromResource(R.xml.settings_integrations);

        // Cache resource IDs
        mMiBand = (CheckBoxPreference) findPreference(getString(R.string.miBandPref));
        mTestIntegrations = findPreference(getString(R.string.integrationsTestPref));

        // Set up listeners
        initializeListeners();
    }

    private void initializeListeners()
    {
        // Test integrations on click listener
        mTestIntegrations.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                // Bluetooth disabled?
                if ( ! BluetoothIntegration.isBluetoothEnabled() )
                {
                    // Ask user to enable bluetooth politely
                    BluetoothDialogs.showEnableBluetoothDialog(DeviceIntegrations.this);

                    // Stop execution (don't consume)
                    return false;
                }

                // Test it out
                BluetoothIntegration.notifyDevices(AlertTypes.TEST, null, DeviceIntegrations.this);

                // Consume event
                return true;
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
