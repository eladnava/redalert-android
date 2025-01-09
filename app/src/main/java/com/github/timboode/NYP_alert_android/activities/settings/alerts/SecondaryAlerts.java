package com.github.timboode.NYP_alert_android.activities.settings.alerts;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.LocationSelectionEvents;
import com.github.timboode.NYP_alert_android.logic.push.PushManager;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;
import com.github.timboode.NYP_alert_android.ui.activities.AppCompatPreferenceActivity;
import com.github.timboode.NYP_alert_android.ui.compatibility.ProgressDialogCompat;
import com.github.timboode.NYP_alert_android.ui.dialogs.AlertDialogBuilder;
import com.github.timboode.NYP_alert_android.ui.elements.SliderPreference;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;
import com.github.timboode.NYP_alert_android.utils.feedback.Volume;
import com.github.timboode.NYP_alert_android.utils.threading.AsyncTaskAdapter;

public class SecondaryAlerts extends AppCompatPreferenceActivity {
    private static final boolean TODO_IS_REGISTERED = true;
    SliderPreference mSecondaryVolume;
    CheckBoxPreference mSecondaryAlertPopup;
    CheckBoxPreference mSecondaryNotificationsEnabled;

    static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1001;

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(LocationSelectionEvents.LOCATIONS_UPDATED)) {
                // Refresh city descriptions
                refreshAlertTopicSubscriptions();

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
        mSecondaryAlertPopup = (CheckBoxPreference)findPreference(getString(R.string.secondaryAlertPopupPref));
        mSecondaryNotificationsEnabled = (CheckBoxPreference)findPreference(getString(R.string.secondaryEnabledPref));

        // Populate setting values
        initializeSettings();

        // Set up listeners
        initializeListeners();
    }

    void initializeSettings() {
        // Not registered yet?
        if (TODO_IS_REGISTERED) { // TODO Replace with real registration check
            mSecondaryNotificationsEnabled.setEnabled(false);
        }

        // Refresh area values
        refreshAlertTopicSubscriptions();
    }

    void refreshAlertTopicSubscriptions() {
        // TODO
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

        // Secondary alert popup checkbox listener
        mSecondaryAlertPopup.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Android M+: Check if we have permission to draw over other apps
                if ((boolean)newValue == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(SecondaryAlerts.this)) {
                    // Show permission request dialog
                    AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission), getString(R.string.grantOverlayPermissionInstructions), getString(R.string.okay), getString(R.string.notNow), true, SecondaryAlerts.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            // Clicked okay?
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                // Bring user to relevant settings activity to grant the app overlay permission
                                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                            }
                        }
                    });

                    // Tell Android *not* to persist new checkbox value
                    return false;
                }

                // Tell Android to persist new checkbox value
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // User returned from manage overlay permission activity?
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            // Android M+: Check if we have permission to draw over other apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(SecondaryAlerts.this)) {
                mSecondaryAlertPopup.setChecked(true);
            }
        }
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
                // TODO: Subscribe to topic
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
                editor.putString(getString(R.string.selectedSecondaryCitiesPref), "TODO"); // TODO: Restore previous topic subscriptions

                // Save and flush to disk
                editor.commit();
            }

            // Activity dead?
            if (isFinishing() || isDestroyed()) {
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
            else {
                // Clear previously cached values
                //TODO
            }

            // Refresh city setting value
            refreshAlertTopicSubscriptions();
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
            if (isFinishing() || isDestroyed()) {
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
