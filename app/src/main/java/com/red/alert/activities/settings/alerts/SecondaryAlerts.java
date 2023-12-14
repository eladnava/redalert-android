package com.red.alert.activities.settings.alerts;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.Log;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.activities.Main;
import com.red.alert.config.Logging;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.logic.push.PushManager;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.compatibility.ProgressDialogCompat;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.threading.AsyncTaskAdapter;

public class SecondaryAlerts extends AppCompatPreferenceActivity {
    String mPreviousSecondaryCities;
    SliderPreference mSecondaryVolume;
    CheckBoxPreference mSecondaryNotificationsEnabled;
    SearchableMultiSelectPreference mSecondaryCitySelection;

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(LocationSelectionEvents.LOCATIONS_UPDATED)) {
                // Refresh city descriptions
                refreshAreaValues();

                // Update subscriptions on the server-side
                new UpdateSubscriptionsAsync().execute();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);
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

        // Load settings from XML (there is no non-deprecated way to do it on API level 7)
        addPreferencesFromResource(R.xml.settings_secondary_alerts);

        // Cache resource IDs
        mSecondaryVolume = ((SliderPreference) findPreference(getString(R.string.secondaryVolumePref)));
        mSecondaryCitySelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedSecondaryCitiesPref)));
        mSecondaryNotificationsEnabled = (CheckBoxPreference)findPreference(getString(R.string.secondaryEnabledPref));

        // Populate setting values
        initializeSettings();

        // Set up listeners
        initializeListeners();
    }

    void initializeSettings() {
        // Set entries & values
        mSecondaryCitySelection.setEntries(LocationData.getAllCityNames(this));
        mSecondaryCitySelection.setEntryValues(LocationData.getAllCityValues(this));

        // Not registered yet?
        if (!RedAlertAPI.isRegistered(this)) {
            mSecondaryNotificationsEnabled.setEnabled(false);
        }

        // Refresh area values
        refreshAreaValues();
    }

    void refreshAreaValues() {
        // Get secondary cities
        String secondaryCities = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedSecondaryCitiesPref), getString(R.string.none));

        // Update summary text
        mSecondaryCitySelection.setSummary(getString(R.string.selectedSecondaryCitiesDesc) + "\r\n(" + LocationData.getSelectedCityNamesByValues(this, secondaryCities, mSecondaryCitySelection.getEntries(), mSecondaryCitySelection.getEntryValues()) + ")");

        // Save in case the update subscriptions request fails
        if (mPreviousSecondaryCities == null) {
            mPreviousSecondaryCities = secondaryCities;
        }
    }

    void initializeListeners() {
        // Volume selection
        mSecondaryVolume.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener() {
            @Override
            public String getDialogMessage(float Value) {
                // Get slider percent
                int percent = (int) (AppPreferences.getSecondaryAlertVolume(SecondaryAlerts.this, Value) * 100);

                // Generate summary
                return getString(R.string.secondaryVolumeDesc) + "\r\n(" + percent + "%)";
            }
        });

        // Secondary notifications toggle listener
        mSecondaryNotificationsEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Update notification preferences on the server-side
                new UpdateNotificationsAsync().execute();

                // Tell Android to persist new checkbox value
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

    public class UpdateSubscriptionsAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        ProgressDialog mLoading;

        public UpdateSubscriptionsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(SecondaryAlerts.this);

            // Prevent cancel
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.loading));

            // Show the progress dialog
            mLoading.show();
        }

        @Override
        protected Exception doInBackground(Integer... Parameter) {
            try {
                // Update Pub/Sub subscriptions
                PushManager.updateSubscriptions(SecondaryAlerts.this);

                // Update alert subscriptions
                RedAlertAPI.subscribe(SecondaryAlerts.this);
            }
            catch (Exception exc) {
                // Return exception to onPostExecute
                return exc;
            }

            // Success
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            // Failed?
            if (exc != null) {
                // Log it
                Log.e(Logging.TAG, "Updating subscriptions failed", exc);

                // Restore previous city selection
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(SecondaryAlerts.this).edit();

                // Restore original value
                editor.putString(getString(R.string.selectedSecondaryCitiesPref), mPreviousSecondaryCities);

                // Save and flush to disk
                editor.commit();
            }

            // Activity dead?
            if (isFinishing()) {
                return;
            }

            // Hide loading dialog
            if (mLoading.isShowing()) {
                try {
                    mLoading.dismiss();
                }
                catch (Exception exc2) {
                    // Most likely, activity was destroyed
                    return;
                }
            }

            // Show error if failed
            if (exc != null) {
                // Build an error message
                String errorMessage = getString(R.string.apiRequestFailed) + "\n\n" + exc.getMessage() + (exc.getCause() != null ? "\n\n" + exc.getCause() : "");

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, SecondaryAlerts.this, null);
            }
            else {
                // Clear previously cached values
                mPreviousSecondaryCities = null;
            }

            // Refresh city setting value
            refreshAreaValues();
        }
    }
    
    public class UpdateNotificationsAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        ProgressDialog mLoading;

        public UpdateNotificationsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(SecondaryAlerts.this);

            // Prevent cancel
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.loading));

            // Show the progress dialog
            mLoading.show();
        }

        @Override
        protected Exception doInBackground(Integer... Parameter) {
            try {
                // Update notification preferences
                PushManager.updateSubscriptions(SecondaryAlerts.this);

                // Update notification preferences
                RedAlertAPI.updateNotificationPreferences(SecondaryAlerts.this);
            }
            catch (Exception exc) {
                // Return exception to onPostExecute
                return exc;
            }

            // Success
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            // Failed?
            if (exc != null) {
                // Log it
                Log.e(Logging.TAG, "Updating notification preferences failed", exc);

                // Restore previous notification toggle state
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(SecondaryAlerts.this).edit();

                // Restore original values
                editor.putBoolean(getString(R.string.secondaryEnabledPref), ! AppPreferences.getSecondaryNotificationsEnabled(SecondaryAlerts.this));

                // Save and flush to disk
                editor.commit();
            }

            // Activity dead?
            if (isFinishing()) {
                return;
            }

            // Hide loading dialog
            if (mLoading.isShowing()) {
                mLoading.dismiss();
            }

            // Show error if failed
            if (exc != null) {
                // Build an error message
                String errorMessage = getString(R.string.apiRequestFailed) + "\n\n" + exc.getMessage() + (exc.getCause() != null ? "\n\n" + exc.getCause() : "");

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, SecondaryAlerts.this, null);
            }

            // Refresh checkbox with new value
            mSecondaryNotificationsEnabled.setChecked(AppPreferences.getSecondaryNotificationsEnabled(SecondaryAlerts.this));
        }
    }
}
