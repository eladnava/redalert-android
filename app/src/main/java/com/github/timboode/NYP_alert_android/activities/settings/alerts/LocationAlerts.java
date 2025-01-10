package com.github.timboode.NYP_alert_android.activities.settings.alerts;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.config.NotificationChannels;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.LocationAlertsEvents;
import com.github.timboode.NYP_alert_android.logic.push.PushManager;
import com.github.timboode.NYP_alert_android.logic.services.ServiceManager;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;
import com.github.timboode.NYP_alert_android.ui.activities.AppCompatPreferenceActivity;
import com.github.timboode.NYP_alert_android.ui.compatibility.ProgressDialogCompat;
import com.github.timboode.NYP_alert_android.ui.dialogs.AlertDialogBuilder;
import com.github.timboode.NYP_alert_android.ui.elements.SliderPreference;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;
import com.github.timboode.NYP_alert_android.utils.feedback.Volume;
import com.github.timboode.NYP_alert_android.utils.localization.Localization;
import com.github.timboode.NYP_alert_android.utils.threading.AsyncTaskAdapter;

public class LocationAlerts extends AppCompatPreferenceActivity {
    private SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure RTL layouts are used if needed
        Localization.overridePhoneLocale(this);

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

    private void initializeUI() {
        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
            mLoading = ProgressDialogCompat.getStyledProgressDialog(LocationAlerts.this);

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
                PushManager.updateSubscriptions(LocationAlerts.this);

                // Subscribe for alerts based on current city/region selections
                // TODO: Subscribe to alerts from backend here
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

                // Restore previous notification toggle state
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(LocationAlerts.this).edit();

                // Restore original values
                editor.putBoolean(getString(R.string.locationAlertsPref), !AppPreferences.getLocationAlertsEnabled(LocationAlerts.this));

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
                String errorMessage = getString(R.string.apiRequestFailed) + "\n\n" + exc.getMessage() + "\n\n" + exc.getCause();

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, LocationAlerts.this, null);
            }
            else {
                    // Android O and newer required for notification channels
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Show dialog instructing user on how to hide the location alerts foreground service notification
                        AlertDialogBuilder.showGenericDialog(getString(R.string.hideGPSForegroundNotification), getString(R.string.hideGPSForegroundNotificationInstructions), getString(R.string.okay), getString(R.string.notNow), true, LocationAlerts.this, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                // Clicked okay?
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                    // Open notification channel config to allow user to easily disable the notification channel
                                    Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_ID);
                                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                                    startActivity(intent);
                                }
                                }
                        });
                    }
                }
        }
    }
}
