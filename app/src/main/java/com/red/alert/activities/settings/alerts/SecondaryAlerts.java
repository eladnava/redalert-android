package com.red.alert.activities.settings.alerts;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.logic.push.PushManager;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.elements.MaterialProgressDialog;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.elements.SliderPreference;

import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.threading.AsyncTaskAdapter;

public class SecondaryAlerts extends AppCompatActivity {
    SecondaryAlertsPreferenceFragment mFragment;

    String mPreviousSecondaryCities;
    SliderPreference mSecondaryVolume;
    SwitchPreferenceCompat mSecondaryAlertPopup;
    SwitchPreferenceCompat mSecondaryNotificationsEnabled;
    SearchableMultiSelectPreference mSecondaryCitySelection;

    static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1001;

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

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

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

    @Override
    protected void attachBaseContext(Context base) {
        // Reapply locale
        Localization.overridePhoneLocale(base);
        super.attachBaseContext(base);
    }

    void initializeUI() {
        // Set up layout with toolbar
        setContentView(R.layout.preference_activity);

        // Set up Material Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Allow click on home button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Load preference fragment
        mFragment = new SecondaryAlertsPreferenceFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.preference_container, mFragment);
        transaction.commit();
    }

    public void onFragmentPreferencesReady() {
        // Fragment has loaded preferences, now we can access them
        if (mFragment != null) {
            mSecondaryVolume = mFragment.getSecondaryVolume();
            mSecondaryAlertPopup = mFragment.getSecondaryAlertPopup();
            mSecondaryCitySelection = mFragment.getSecondaryCitySelection();
            mSecondaryNotificationsEnabled = mFragment.getSecondaryNotificationsEnabled();

            // Populate setting values
            initializeSettings();

            // Set up listeners
            initializeListeners();
        }
    }

    public void onPreferenceChanged(String key) {
        // Handle preference changes - delegate to existing broadcast listener
        if (mBroadcastListener != null) {
            SharedPreferences prefs = mFragment != null && mFragment.getPreferenceScreen() != null
                    ? mFragment.getPreferenceScreen().getSharedPreferences()
                    : Singleton.getSharedPreferences(this);
            mBroadcastListener.onSharedPreferenceChanged(prefs, key);
        }
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
        String secondaryCities = Singleton.getSharedPreferences(this)
                .getString(getString(R.string.selectedSecondaryCitiesPref), getString(R.string.none));

        // Update summary text
        mSecondaryCitySelection
                .setSummary(
                        getString(R.string.selectedSecondaryCitiesDesc) + "\r\n("
                                + LocationData.getSelectedCityNamesByValues(this, secondaryCities,
                                        mSecondaryCitySelection.getEntries(), mSecondaryCitySelection.getEntryValues())
                                + ")");

        // Save in case the update subscriptions request fails
        if (mPreviousSecondaryCities == null) {
            mPreviousSecondaryCities = secondaryCities;
        }
    }

    void initializeListeners() {

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
                if ((boolean) newValue == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(SecondaryAlerts.this)) {
                    // Show permission request dialog
                    AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission),
                            getString(R.string.grantOverlayPermissionInstructions), getString(R.string.okay),
                            getString(R.string.notNow), true, SecondaryAlerts.this,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int which) {
                                    // Clicked okay?
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        // Bring user to relevant settings activity to grant the app overlay permission
                                        startActivityForResult(
                                                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:" + getPackageName())),
                                                ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
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
        MaterialProgressDialog mLoading;

        public UpdateSubscriptionsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = new MaterialProgressDialog(SecondaryAlerts.this);

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
            } catch (Exception exc) {
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
                String errorMessage = getString(R.string.apiRequestFailed) + "\n\n" + exc.getMessage()
                        + (exc.getCause() != null ? "\n\n" + exc.getCause() : "");

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay),
                        null, false, SecondaryAlerts.this, null);
            } else {
                // Clear previously cached values
                mPreviousSecondaryCities = null;
            }

            // Refresh city setting value
            refreshAreaValues();
        }
    }

    public class UpdateNotificationsAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        MaterialProgressDialog mLoading;

        public UpdateNotificationsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = new MaterialProgressDialog(SecondaryAlerts.this);

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
            } catch (Exception exc) {
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
                editor.putBoolean(getString(R.string.secondaryEnabledPref),
                        !AppPreferences.getSecondaryNotificationsEnabled(SecondaryAlerts.this));

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
                String errorMessage = getString(R.string.apiRequestFailed) + "\n\n" + exc.getMessage()
                        + (exc.getCause() != null ? "\n\n" + exc.getCause() : "");

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay),
                        null, false, SecondaryAlerts.this, null);
            }

            // Refresh checkbox with new value
            mSecondaryNotificationsEnabled
                    .setChecked(AppPreferences.getSecondaryNotificationsEnabled(SecondaryAlerts.this));
        }
    }
}
