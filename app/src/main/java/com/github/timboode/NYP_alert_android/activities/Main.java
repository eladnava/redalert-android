package com.github.timboode.NYP_alert_android.activities;

import android.app.AlarmManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.activities.settings.General;
import com.github.timboode.NYP_alert_android.config.Integrations;
import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.config.RecentAlerts;
import com.github.timboode.NYP_alert_android.config.ThreatTypes;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.SettingsEvents;
import com.github.timboode.NYP_alert_android.logic.communication.intents.AlertPopupParameters;
import com.github.timboode.NYP_alert_android.logic.communication.intents.MainActivityParameters;
import com.github.timboode.NYP_alert_android.logic.permissions.AndroidPermissions;
import com.github.timboode.NYP_alert_android.logic.services.ServiceManager;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;
import com.github.timboode.NYP_alert_android.model.Alert;
import com.github.timboode.NYP_alert_android.ui.adapters.AlertAdapter;
import com.github.timboode.NYP_alert_android.ui.compatibility.ProgressDialogCompat;
import com.github.timboode.NYP_alert_android.ui.dialogs.AlertDialogBuilder;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.ui.notifications.AppNotifications;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;
import com.github.timboode.NYP_alert_android.utils.feedback.Volume;
import com.github.timboode.NYP_alert_android.utils.os.AndroidSettings;
import com.github.timboode.NYP_alert_android.utils.localization.DateTime;
import com.github.timboode.NYP_alert_android.utils.localization.Localization;
import com.github.timboode.NYP_alert_android.utils.threading.AsyncTaskAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends AppCompatActivity {
    boolean mIsResumed;
    boolean mIsReloading;
    boolean mIsRegistering;
    boolean mCheckedForUpdates;
    boolean mPushTokensRefreshed;
    boolean mPermissionDialogDisplayed;

    Button mImSafe;
    ListView mAlertsList;
    ProgressBar mLoading;
    MenuItem mLoadingItem;
    LinearLayout mNoAlerts;
    AlertAdapter mAlertsAdapter;
    MenuItem mClearRecentAlertsItem;

    List<Alert> mNewAlerts;
    List<Alert> mDisplayAlerts;
    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(MainActivityParameters.RELOAD_RECENT_ALERTS)) {
                reloadRecentAlerts();
            }

            // Language changed?
            if (Key.equalsIgnoreCase(SettingsEvents.THEME_OR_LANGUAGE_CHANGED)) {
                // Reload activity
                finish();
                startActivity(new Intent(Main.this, Main.class));
            }
        }
    };

    private static final Boolean TODO_INSERT_REGISTRATION_CHECK = true; // TODO: Remove and replace with real registration check
    private static final Boolean TODO_PUSH_SERVICE_CONNECTED = true; // TODO: Remove and replace with real push connected check

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Apply custom theme selection (make sure to invoke this before super.onCreate())
        Localization.applyThemeSelection(this);

        // Call parent method
        super.onCreate(savedInstanceState);

        // Ensure RTL layouts are used if needed
        Localization.overridePhoneLocale(this);

        // Initialize app UI
        initializeUI();

        // Start polling for recent alerts
        pollRecentAlerts();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);

        // Handle notification click event (show alert popup)
        handleNotificationClick(getIntent());
    }

    void showAppUpdateAvailableDialog() {
        // Haven't displayed tutorial?
        if (!AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Haven't registered with FCM/Pushy for notifications?
        if (TODO_INSERT_REGISTRATION_CHECK) { //TODO PUSH NOTIFICATION REGISTRATION
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Clear app notifications
        AppNotifications.clearAll(this);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Handle notification click event (show alert popup)
        handleNotificationClick(intent);
    }

    void initializeUI() {
        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set title manually (for override locale to work)
        setTitle(R.string.recentAlerts);

        // Set up UI
        setContentView(R.layout.main);

        // Get views by IDs
        mImSafe = (Button) findViewById(R.id.safe);
        mAlertsList = (ListView) findViewById(R.id.alerts);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mNoAlerts = (LinearLayout) findViewById(R.id.noAlerts);

        // Initialize alert lists
        mNewAlerts = new ArrayList<>();
        mDisplayAlerts = new ArrayList<>();

        // Create alert adapter
        mAlertsAdapter = new AlertAdapter(this, mDisplayAlerts);

        // Attach it to list
        mAlertsList.setAdapter(mAlertsAdapter);

        // On-click listeners
        initializeListeners();
    }

    void initializeListeners() {
        // Alert click listener
        mAlertsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                // Get alert by click position
                Alert alert = mDisplayAlerts.get(position);

                // No such alert?
                if (alert == null) {
                    return;
                }

                // No map view for system alerts
                if (alert.threat.equals(ThreatTypes.SYSTEM)) {
                    return;
                }

                // Create new intent
                Intent alertView = new Intent();

                // Set class
                //alertView.setClass(Main.this, Map.class); // TODO: Replace MAP class which used to be a google maps alert location view with a custom alert view

                //alertView.putExtra(AlertViewParameters.ALERTS, "TODO: ALERT MESSAGE"); // TODO: This is where the validated message sent by NYPrepper should be inserted

                // Show it
                //startActivity(alertView);
                Toast.makeText(Main.this, "Alerts are not yet supported", Toast.LENGTH_LONG).show();
            }
        });

        // No alerts image click listener
        mNoAlerts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // DEBUG ONLY
                //AlertLogic.processIncomingAlert("missiles", "נהריה", "alert", null, Main.this);
            }
        });

        // "I'm Safe" button click listener
        mImSafe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Prepare share intent
                Intent shareIntent = new Intent(Intent.ACTION_SEND);

                // Set as text/plain
                shareIntent.setType("text/plain");

                // Add text
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.imSafeMessage));

                // Send via WhatsApp?
                if (Singleton.getSharedPreferences(Main.this).getBoolean(getString(R.string.imSafeWhatsAppPref), false)) {
                    // Set WhatsApp package
                    shareIntent.setPackage(Integrations.WHATSAPP_PACKAGE);
                }

                // Show chooser
                startActivity(Intent.createChooser(shareIntent, getString(R.string.imSafeDesc)));
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensure RTL layouts are used if needed
        Localization.overridePhoneLocale(this);

        // Save state
        mIsResumed = true;

        // Allow other dialogs to be displayed
        mPermissionDialogDisplayed = false;

        // Reload alerts manually
        reloadRecentAlerts();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Clear app notifications
        AppNotifications.clearAll(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);

        // Android 13:
        // Ensure notification permission has been granted
        requestNotificationPermission();

        // Ask user to whitelist app from battery optimizations
        showBatteryExemptionDialog();

        // Android 12+
        // Ask user to allow setting exact alarms if canScheduleExactAlarms() is false (usually only false on Android 13+, but can be disabled manually since Android 12)
        showScheduleExactAlarmsPermissionDialog();

        // Ask user to grant app overlay permission if revoked
        showAlertPopupPermissionDialog();

        // Check for app version updates
        showAppUpdateAvailableDialog();

        // Restart the app services if needed
        ServiceManager.startAppServices(this);

        // Always re-register FCM or Pushy on app start
        if (!mPushTokensRefreshed || TODO_INSERT_REGISTRATION_CHECK) { // TODO: Replace TODO_INSERT_REGISTRATION_CHECK with a push notification registration check
            // Prevent concurrent registration
            if (!mIsRegistering) {
                new RegisterPushAsync().execute();
            }
        }

        // Already loaded recent alerts?
        if (mNewAlerts.size() > 0) {
            // Refresh alerts relative time
            invalidateAlertList();
        }
    }

    void requestNotificationPermission() {
        // Check if device is already registered, but permission not granted yet
        if (TODO_PUSH_SERVICE_CONNECTED && (!AndroidPermissions.isNotificationPermissionGranted(this) || !NotificationManagerCompat.from(Main.this).areNotificationsEnabled())) { // TODO: Replace TODO_PUSH_SERVICE_CONNECTED with a push notification registration check
            // Prevent other dialogs from being displayed
            mPermissionDialogDisplayed = true;

            // Show error with an on-click listener that opens the App Info page
            AlertDialogBuilder.showGenericDialog(getString(R.string.error), getString(R.string.grantNotificationPermission), getString(R.string.okay), null, false, Main.this, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Open App Info settings page
                    AndroidSettings.openAppInfoPage(Main.this);
                }
            });
        }
    }

    void showScheduleExactAlarmsPermissionDialog() {
        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Haven't displayed tutorial?
        if (!AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Haven't registered for notifications?
        if (TODO_INSERT_REGISTRATION_CHECK) { // TODO: Replace TODO_INSERT_REGISTRATION_CHECK with a push notification registration check
            return;
        }

        // Only Android 12 and up allows revoking schedule exact alarms permission
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }

        // Get power manager instance
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // This dialog should only be displayed after user disabled battery optimizations
        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }

        // Get alarm manager instance
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        // Check if user has already granted permission
        if (alarmManager.canScheduleExactAlarms()) {
            return;
        }

        // Show a dialog to the user
        AlertDialogBuilder.showGenericDialog(getString(R.string.allowSettingExactAlarms), getString(R.string.allowSettingExactAlarmsInstructions), getString(R.string.okay), getString(R.string.notNow), true, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // Clicked okay?
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Open alarms and reminders settings screen for this app
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);

                    // Set package to current package
                    intent.setData(Uri.fromParts("package", getPackageName(), null));

                    // Start settings activity
                    startActivity(intent);
                }
            }
        });

        // Prevent other dialogs from being displayed
        mPermissionDialogDisplayed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save state
        mIsResumed = false;
    }

    void showBatteryExemptionDialog() {
        // Android M (6) and up only
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        // Haven't displayed tutorial?
        if (!AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Haven't registered for notifications?
        if (TODO_INSERT_REGISTRATION_CHECK) { // TODO: Replace TODO_INSERT_REGISTRATION_CHECK with a push notification registration check
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Get power manager instance
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Check if app isn't already whitelisted from battery optimizations
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }

        // Check how many times we asked already
        int count = Singleton.getSharedPreferences(this).getInt(getString(R.string.batteryOptimizationWarningDisplayedCountPref), 0);

        // Warn 3 times
        if (count >= 3) {
            return;
        }

        // Show a dialog to the user
        AlertDialogBuilder.showGenericDialog(getString(R.string.disableBatteryOptimizations), AndroidSettings.getBatteryOptimizationWhitelistInstructions(this), getString(R.string.okay), getString(R.string.notNow), true, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // Clicked okay?
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Open App Info settings page
                    AndroidSettings.openAppInfoPage(Main.this);
                }
            }
        });

        // Prevent other dialogs from being displayed
        mPermissionDialogDisplayed = true;

        // Increment counter
        count++;

        // Persist dialog display counter to SharedPreferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(this).edit();

        // Save counter as integer
        editor.putInt(getString(R.string.batteryOptimizationWarningDisplayedCountPref), count);

        // Save and flush to disk
        editor.commit();
    }

    void showAlertPopupPermissionDialog() {
        // Android M (6) and up only
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        // Check if alert popup disabled (primary/secondary)
        if (!AppPreferences.getPopupEnabled(this) && !AppPreferences.getSecondaryPopupEnabled(this)) {
            return;
        }

        // Check if we already have permission to draw over other apps
        if (Settings.canDrawOverlays(this)) {
            return;
        }

        // Haven't displayed tutorial?
        if (!AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Haven't registered for notifications?
        if (TODO_INSERT_REGISTRATION_CHECK) { // TODO: Replace TODO_INSERT_REGISTRATION_CHECK with a push notification registration check
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Prevent other dialogs from being displayed
        mPermissionDialogDisplayed = true;

        // Show permission request dialog
        AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission), getString(R.string.grantOverlayPermissionInstructions), getString(R.string.okay), getString(R.string.notNow), true, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // Clicked okay?
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Bring user to relevant settings activity to grant the app overlay permission
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                }
            }
        });
    }

    void pollRecentAlerts() {
        // Schedule a new timer
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // App is running?
                        if (mIsResumed) {
                            // Reload every X seconds
                            reloadRecentAlerts();
                        }
                    }
                });
            }
        }, 0, 1000 * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC);
    }

    void reloadRecentAlerts() {
        // Not already reloading?
        if (!mIsReloading) {
            // Get recent alerts async
            new GetRecentAlertsAsync().execute();
        }
    }

    void initializeSettingsButton(Menu OptionsMenu) {
        // Add settings item
        MenuItem settingsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.settings));

        // Set settings icon
        settingsItem.setIcon(R.drawable.ic_settings);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(settingsItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, open Settings activity
        settingsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Start settings activity
                goToSettings(false);

                // Consume event
                return true;
            }
        });
    }

    void initializeClearRecentAlertsButton(Menu OptionsMenu) {
        // Add clear item
        mClearRecentAlertsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.clearRecentAlerts));

        // Set default icon
        mClearRecentAlertsItem.setIcon(R.drawable.ic_clear);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mClearRecentAlertsItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, toggle clearing recent alerts
        mClearRecentAlertsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Clear currently displayed alerts
                if (mDisplayAlerts.size() > 0) {
                    // Show a dialog to the user
                    AlertDialogBuilder.showGenericDialog(getString(R.string.clearRecentAlerts), getString(R.string.clearRecentAlertsDesc), getString(R.string.yes), getString(R.string.no), true, Main.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            // Clicked okay?
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                // Set recent alerts cutoff timestamp to now
                                AppPreferences.updateRecentAlertsCutoffTimestamp(DateTime.getUnixTimestamp(), Main.this);

                                // Invalidate the list
                                invalidateAlertList();

                                // Clear app notifications
                                AppNotifications.clearAll(Main.this);
                            }
                        }
                    });
                }
                else {
                    // No alerts displayed, so display all of them
                    AppPreferences.updateRecentAlertsCutoffTimestamp(0, Main.this);

                    // Invalidate the list
                    invalidateAlertList();
                }

                // Consume event
                return true;
            }
        });

        // By default, hide button until alerts are returned by the server
        mClearRecentAlertsItem.setVisible(false);
    }

    void goToSettings(boolean showZoneSelection) {
        // Prepare new intent
        Intent settingsIntent = new Intent();

        // Set settings class
        settingsIntent.setClass(Main.this, General.class);

        // Add area selection boolean
        settingsIntent.putExtra(SettingsEvents.SHOW_TOPIC_SELECTION, showZoneSelection);

        // Start settings activity
        startActivity(settingsIntent);
    }

    void initializeLoadingIndicator(Menu OptionsMenu) {
        // Add refresh in Action Bar
        mLoadingItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.loading));

        // Set up the view
        MenuItemCompat.setActionView(mLoadingItem, R.layout.loading_small);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mLoadingItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Hide by default
        mLoadingItem.setVisible(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu) {
        // Add loading indicator
        initializeLoadingIndicator(OptionsMenu);

        // Add clear recent alerts button
        initializeClearRecentAlertsButton(OptionsMenu);

        // Add settings button
        initializeSettingsButton(OptionsMenu);

        // Show the menu
        return true;
    }

    private int getRecentAlerts() {
        // Ensure the right language is displayed
        Localization.overridePhoneLocale(this);

        // Prepare tmp object list
        List<Alert> recentAlerts = new ArrayList<>();

        // TODO: Populate recent alerts

        // Clear global list
        mNewAlerts.clear();

        // Add all the new alerts
        mNewAlerts.addAll(recentAlerts);

        // Success
        return 0;
    }

    void toggleProgressBarVisibility(boolean visibility) {
        // Set loading visibility
        if (mLoadingItem != null) {
            mLoadingItem.setVisible(visibility);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    void invalidateAlertList() {
        // Workaround for ConcurrentModificationException
        // Wait for reloading to complete
        if (mIsReloading) {
            return;
        }

        // Clear global list
        mDisplayAlerts.clear();

        // Check if user cleared the recent alerts
        final long cutoffTimestamp = AppPreferences.getRecentAlertsCutoffTimestamp(Main.this);

        // Remove alerts that occurred before the cutoff
        if (cutoffTimestamp > 0) {
            // Traverse alerts
            for (Alert alert : mNewAlerts) {
                // Check if alert date is after cutoff timestamp
                if (alert.date > cutoffTimestamp) {
                    // Add to display list
                    mDisplayAlerts.add(alert);
                }
            }
        }
        else {
            // No alert date cutoff, add all the new alerts
            mDisplayAlerts.addAll(mNewAlerts);
        }

        // Invalidate the list
        mAlertsAdapter.notifyDataSetChanged();

        // No alerts? Show default
        if (mDisplayAlerts.size() == 0) {
            mNoAlerts.setVisibility(View.VISIBLE);
        }
        else {
            mNoAlerts.setVisibility(View.GONE);
        }

        // Update displayed icon according to how many alerts were returned from the server
        updateClearAlertsButton(cutoffTimestamp);
    }

    void updateClearAlertsButton(long cutoffTimestamp) {
        // Null pointer check
        if (mClearRecentAlertsItem == null) {
            return;
        }

        // By default, show clear button
        mClearRecentAlertsItem.setVisible(true);

        // In case no alerts are being displayed but alerts were returned
        if (cutoffTimestamp > 0 && mNewAlerts.size() > 0 && mDisplayAlerts.size() == 0) {
            // Show restore icon to allow user to restore all recent alerts
            mClearRecentAlertsItem.setIcon(R.drawable.ic_restore);
        }
        // In case there are no alerts in the past 24 hours
        else if (mNewAlerts.size() == 0) {
            // Hide clear button
            mClearRecentAlertsItem.setVisible(false);
        }
        else {
            // There are alerts, show default clear icon
            mClearRecentAlertsItem.setIcon(R.drawable.ic_clear);
        }
    }

    void showRegistrationSuccessDialog() {
        // Already displayed?
        if (AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Tutorial was displayed
        AppPreferences.setTutorialDisplayed(this);
    }

    public class GetRecentAlertsAsync extends AsyncTaskAdapter<Integer, String, Integer> {
        public GetRecentAlertsAsync() {
            // Prevent concurrent reload
            mIsReloading = true;

            // Show loading indicator
            toggleProgressBarVisibility(true);
        }

        @Override
        protected Integer doInBackground(Integer... Parameter) {
            // Try to get recent alerts
            return getRecentAlerts();
        }

        @Override
        protected void onPostExecute(Integer errorStringResource) {
            // No longer reloading
            mIsReloading = false;

            // Activity dead?
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // Hide loading
            mLoading.setVisibility(View.GONE);

            // Hide loading indicator
            toggleProgressBarVisibility(false);

            // Success?
            if (errorStringResource == 0) {
                // Invalidate the list
                invalidateAlertList();
            }
            else {
                // Show error toast
                Toast.makeText(Main.this, getString(errorStringResource), Toast.LENGTH_LONG).show();
            }
        }
    }

    public class RegisterPushAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        ProgressDialog mLoading;

        public RegisterPushAsync() {
            // Prevent concurrent registration
            mIsRegistering = true;

            // Set push tokens as refreshed
            mPushTokensRefreshed = true;

            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(Main.this);

            // Prevent cancel
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.signing_up));

            // First time registering?
            if (TODO_INSERT_REGISTRATION_CHECK) { // TODO: Replace TODO_INSERT_REGISTRATION_CHECK with a push notification registration check
                // Show the progress dialog
                mLoading.show();
            }
        }

        @Override
        protected Exception doInBackground(Integer... Parameter) {

            // Retry mechanism (for temporary network errors)
            int tries = 0;

            // Retry up to 5 times
            while (tries <= 5) {
                try {
                    // TODO: Get latest notifications/alerts here....

                    // If we're here, success
                    break;
                } catch (Exception exc) {
                    // Throw exception after 5 tries
                    if (tries > 5) {
                        return exc;
                    }
                    else {
                        // Log error
                        Log.e(Logging.TAG, "Push registration failed, retrying...", exc);

                        try {
                            // Wait a second and try again
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e) {}
                    }
                }
            }

            // Success
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            // No longer registering
            mIsRegistering = false;

            // Activity dead?
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // Hide loading
            if (mLoading.isShowing()) {
                mLoading.dismiss();
            }

            // Failed?
            if (exc != null) {
                // Log it
                Log.e(Logging.TAG, "Push registration failed", exc);

                // Build an error message
                String errorMessage = getString(R.string.pushRegistrationFailed) + "\n\n" + exc.getMessage() + (exc.getCause() != null ? "\n\n" + exc.getCause() : "");

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, Main.this, null);
            }
            else {
                // Success, show success dialog if haven't yet
                showRegistrationSuccessDialog();
            }
        }
    }

    void handleNotificationClick(Intent intent) {
        // Extract clicked notification params
        String threatType = intent.getStringExtra(AlertPopupParameters.THREAT_TYPE);
        long timestamp = intent.getLongExtra(AlertPopupParameters.TIMESTAMP, 0);
        long validCountdown = intent.getLongExtra(AlertPopupParameters.TIMESTAMP, -1);

        // Display popup if all necessary parameters are set
        if (threatType != null && timestamp > 0) {

            // Check for old (inactive) alert
            // Only display alert popup for currently active alerts (+ 10 minutes)
            if (timestamp < (DateTime.getUnixTimestamp() - validCountdown)) {
                return;
            }

            // Create new popup intent
            final Intent popupIntent = new Intent();

            // Set class to popup activity
            popupIntent.setClass(this, Popup.class);

            // Pass on threat type, and alert received timestamp
            popupIntent.putExtra(AlertPopupParameters.TIMESTAMP, timestamp);
            popupIntent.putExtra(AlertPopupParameters.THREAT_TYPE, threatType);

            // Clear top, set as new task
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            // Delay popup by 300ms to allow main activity UI to finish rendering
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Display popup activity
                    startActivity(popupIntent);
                }
            }, 300);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Just granted location permission?
        if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            // Location alerts enabled?
            if (AppPreferences.getLocationAlertsEnabled(this)) {
                // Start the location service
                ServiceManager.startPushNotificationService(this);
            }
        }
    }
}
