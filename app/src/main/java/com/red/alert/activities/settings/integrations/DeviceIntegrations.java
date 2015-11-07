package com.red.alert.activities.settings.integrations;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.Log;
import android.view.MenuItem;

import com.betomaluje.miband.ActionCallback;
import com.betomaluje.miband.MiBand;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.config.Testing;
import com.red.alert.logic.integration.BluetoothIntegration;
import com.red.alert.logic.integration.devices.MiBandIntegration;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.compatibility.ProgressDialogCompat;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.dialogs.custom.BluetoothDialogs;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.threading.AsyncTaskAdapter;

public class DeviceIntegrations extends AppCompatPreferenceActivity
{
    boolean mIsTesting;
    boolean mMiBandTestPassed;

    boolean mIsDestroyed;

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
                if (!BluetoothIntegration.isBluetoothEnabled())
                {
                    // Ask user to enable bluetooth politely
                    BluetoothDialogs.showEnableBluetoothDialog(DeviceIntegrations.this);

                    // Stop execution (don't consume)
                    return false;
                }

                // Not already testing?
                if (!mIsTesting)
                {
                    // Test out the integrations
                    new PerformIntegrationsTestAsync().execute();
                }

                // Consume event
                return true;
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // Avoid hiding invalid dialogs
        mIsDestroyed = true;
    }

    private void testMiBandConnectivity()
    {
        // Reset test result
        mMiBandTestPassed = false;

        // Get an instance of the Mi Band SDK
        final MiBand miBand = MiBand.getInstance(this);

        // Attempt to connect to it
        miBand.connect(new ActionCallback()
        {
            @Override
            public void onSuccess(Object data)
            {
                // Set passed boolean
                mMiBandTestPassed = true;
            }

            @Override
            public void onFail(int errorCode, String msg)
            {
                // Failed, log it
                Log.d(Logging.TAG, "Failed to connect to Mi Band: " + msg);
            }
        });
    }

    private void waitForMiBandConnectivityResult()
    {
        // Calculate the max timestamp
        long maxTimestamp = System.currentTimeMillis() + Testing.DEVICE_CONNECTION_TIMEOUT_SECONDS * 1000;

        // Wait until boolean value changes or enough time passes
        while (!mMiBandTestPassed && System.currentTimeMillis() < maxTimestamp)
        {
            try
            {
                // Sleep to relieve the thread
                Thread.sleep(100);
            }
            catch (Exception exc){}
        }
    }

    public class PerformIntegrationsTestAsync extends AsyncTaskAdapter<Integer, String, Integer>
    {
        ProgressDialog mLoading;

        public PerformIntegrationsTestAsync()
        {
            // Prevent concurrent testing
            mIsTesting = true;

            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(DeviceIntegrations.this);

            // Prevent cancellation
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.loading));

            // Show the progress dialog
            mLoading.show();
        }

        @Override
        protected void onProgressUpdate(String... value)
        {
            super.onProgressUpdate(value);

            // Update progress dialog
            mLoading.setMessage(value[0]);
        }

        @Override
        protected Integer doInBackground(Integer... Parameter)
        {
            // Set progress dialog status text
            publishProgress(getString(R.string.connecting));

            // Xiaomi Mi Band integration enabled?
            if (AppPreferences.getMiBandIntegrationEnabled(DeviceIntegrations.this))
            {
                // Try to connect (async)
                testMiBandConnectivity();

                // Wait for result or until enough time passes
                waitForMiBandConnectivityResult();

                // Mi Band connection failed?
                if ( ! mMiBandTestPassed )
                {
                    // Return error
                    return R.string.miBandTestFailed;
                }
                else
                {
                    // Send Vibration + LED colors
                    MiBandIntegration.sendNotificationCommands(DeviceIntegrations.this);
                }
            }

            // We're good
            return 0;
        }

        @Override
        protected void onPostExecute(Integer errorStringResource)
        {
            // No longer testing
            mIsTesting = false;

            // Activity dead?
            if (isFinishing() || mIsDestroyed)
            {
                return;
            }

            // Hide progress dialog
            if (mLoading.isShowing())
            {
                mLoading.dismiss();
            }

            // Success?
            if ( errorStringResource == 0 )
            {
                // Build the success dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.testSuccessful), getString(R.string.integrationsTestSuccess), DeviceIntegrations.this, null);
            }
            else
            {
                // Build the error dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), getString(errorStringResource), DeviceIntegrations.this, null);
            }
        }
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
