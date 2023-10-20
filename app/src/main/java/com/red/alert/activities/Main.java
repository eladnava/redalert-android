package com.red.alert.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.PowerManager;
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

import me.pushy.sdk.Pushy;
import me.pushy.sdk.lib.jackson.core.type.TypeReference;
import me.pushy.sdk.util.PushyAuthentication;

import com.red.alert.R;
import com.red.alert.activities.settings.General;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.config.API;
import com.red.alert.config.Alerts;
import com.red.alert.config.Integrations;
import com.red.alert.config.Logging;
import com.red.alert.config.RecentAlerts;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.logic.push.PushManager;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.logic.services.ServiceManager;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.Alert;
import com.red.alert.model.res.VersionInfo;
import com.red.alert.ui.adapters.AlertAdapter;
import com.red.alert.ui.compatibility.ProgressDialogCompat;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.dialogs.custom.UpdateDialogs;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.integration.GooglePlayServices;
import com.red.alert.utils.intents.AndroidSettings;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import org.ocpsoft.prettytime.PrettyTime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends AppCompatActivity {
    boolean mIsResumed;
    boolean mIsDestroyed;
    boolean mIsReloading;
    boolean mBatteryDialogDisplayed;

    Button mImSafe;
    ListView mAlertsList;
    ProgressBar mLoading;
    MenuItem mLoadingItem;
    LinearLayout mNoAlerts;
    AlertAdapter mAlertsAdapter;

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
            if (Key.equalsIgnoreCase(SettingsEvents.LANGUAGE_CHANGED)) {
                // Reload activity
                finish();
                startActivity(new Intent(Main.this, Main.class));
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize app UI
        initializeUI();

        // Start polling for recent alerts
        pollRecentAlerts();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);

        // Start the app services
        ServiceManager.startAppServices(this);

        // Check for app version updates
        initializeUpdateChecker();

        // Always re-register FCM or Pushy on app start
        new RegisterPushAsync().execute();
    }

    void initializeUpdateChecker() {
        // Do it async
        new CheckForUpdatesAsync().execute();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Clear app notifications
        AppNotifications.clearAll(this);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);
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

        // Initialize alert list
        mNewAlerts = new ArrayList<>();
        mDisplayAlerts = new ArrayList<>();

        // Create alert adapter
        mAlertsAdapter = new AlertAdapter(this, mDisplayAlerts);

        // Attach it to list
        mAlertsList.setAdapter(mAlertsAdapter);

        // On-click listeners
        initializeListeners();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Avoid call to super(). Bug on API Level > 11.
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
                if (alert.threat.equals(AlertTypes.SYSTEM)) {
                    return;
                }

                // Create new intent
                Intent alertView = new Intent();

                // Set class
                alertView.setClass(Main.this, AlertView.class);

                // Push extras
                alertView.putExtra(AlertViewParameters.ALERT_DATE_STRING, alert.dateString);
                alertView.putExtra(AlertViewParameters.ALERT_CITIES, alert.groupedCities.toArray(new String[0]));

                // Show it
                startActivity(alertView);
            }
        });

        // No alerts image click listener
        mNoAlerts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // DEBUG ONLY
                //AlertLogic.processIncomingAlert("missiles", "נהריה", "alert", Main.this);
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
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Save state
        mIsResumed = true;

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
    }

    void requestNotificationPermission() {
        // Android 13 only
        // Check if device is already registered, but permission not granted yet
        if (PushyAuthentication.getDeviceCredentials(this) != null && !Pushy.isPermissionGranted(this)) {
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
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this)) {
            return;
        }

        // Android 13 notification permission not granted yet?
        if (!Pushy.isPermissionGranted(this)) {
            return;
        }

        // Already displayed for this activity?
        if (mBatteryDialogDisplayed) {
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

        // Show the dialog
        AlertDialogBuilder.showGenericDialog(getString(R.string.disableBatteryOptimizations), getString(R.string.disableBatteryOptimizationsInstructions), getString(R.string.okay), getString(R.string.notNow), true, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // Clicked okay?
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Open App Info settings page
                    AndroidSettings.openAppInfoPage(Main.this);
                }
            }
        });

        // Don't show again for this activity
        mBatteryDialogDisplayed = true;

        // Increment counter
        count++;

        // Persist dialog display counter to SharedPreferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(this).edit();

        // Save counter as integer
        editor.putInt(getString(R.string.batteryOptimizationWarningDisplayedCountPref), count);

        // Save and flush to disk
        editor.commit();
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
        // Add refresh in Action Bar
        MenuItem settingsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.settings));

        // Set up the view
        settingsItem.setIcon(R.drawable.ic_settings);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(settingsItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, go to Settings
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

    void goToSettings(boolean showZoneSelection) {
        // Prepare new intent
        Intent settingsIntent = new Intent();

        // Set settings class
        settingsIntent.setClass(Main.this, General.class);

        // Add area selection boolean
        settingsIntent.putExtra(SettingsEvents.SHOW_ZONE_SELECTION, showZoneSelection);

        // Start settings activity
        startActivity(settingsIntent);
    }

    void initializeLoadingIndicator(Menu OptionsMenu) {
        // Add refresh in Action Bar
        mLoadingItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.loading));

        // Set up the view
        MenuItemCompat.setActionView(mLoadingItem, R.layout.loading);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mLoadingItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Hide by default
        mLoadingItem.setVisible(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu) {
        // Add loading indicator
        initializeLoadingIndicator(OptionsMenu);

        // Add settings button
        initializeSettingsButton(OptionsMenu);

        // Show the menu
        return true;
    }

    private int getRecentAlerts() {
        // Store JSON as string initially
        String alertsJSON;

        try {
            // Get it from /alerts
            alertsJSON = HTTP.get("/alerts");
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.apiRequestFailed;
        }

        // Prepare tmp object list
        List<Alert> recentAlerts;

        try {
            // Convert JSON to object
            recentAlerts = Singleton.getJackson().readValue(alertsJSON, new TypeReference<List<Alert>>() {
            });
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.jsonFailed;
        }

        // For debugging purposes
        // Alert fake = new Alert();
        // fake.city = "אבו סנאן";
        // fake.date = 1562037706;
        // fake.threat = "missiles";

        // recentAlerts.add(fake);

        // Initialize date format libraries
        SimpleDateFormat dateFormat = new SimpleDateFormat(Alerts.DATE_FORMAT);
        PrettyTime relativeFormat = new PrettyTime(getResources().getConfiguration().locale);

        // Loop over alerts
        for (Alert alert : recentAlerts) {
            // Convert unix timestamp to Date object
            Date date = new Date(alert.date * 1000);

            // Prepare string with relative time ago and fixed HH:mm:ss
            alert.dateString = StringUtils.capitalize(relativeFormat.format(date)) + " (" + dateFormat.format(date) + ")";

            // Fix for Hebrew relative time typos (singular time ago)
            alert.dateString = alert.dateString.replace(" 1 דקה", " דקה");
            alert.dateString = alert.dateString.replace(" 1 שעה", " שעה");
            alert.dateString = alert.dateString.replace(" 2 שעות", " שעתיים");

            // Prepare localized zone for display
            alert.zone = LocationData.getLocalizedZoneByCityName(alert.city, this);
            alert.desc = alert.zone;

            // Localize it
            alert.localizedCity = LocationData.getLocalizedCityName(alert.city, this);
            alert.localizedThreat = LocationData.getLocalizedThreatType(alert.threat, this);
        }

        // Group alerts with same timestamp
        recentAlerts = groupAlerts(recentAlerts);

        // Clear global list
        mNewAlerts.clear();

        // Add all the new alerts
        mNewAlerts.addAll(recentAlerts);

        // Success
        return 0;
    }

    private List<Alert> groupAlerts(List<Alert> alerts) {
        // Prepare grouped alerts list
        List<Alert> groupedAlerts = new ArrayList<>();

        // Keep track of last alert item added to list
        Alert lastAlert = null;

        // Traverse elements
        for (int i = 0; i < alerts.size(); i++) {
            // Current element
            Alert currentAlert = alerts.get(i);

            // Initialize city names list for map display
            currentAlert.groupedCities = new ArrayList<>();
            currentAlert.groupedCities.add(currentAlert.city);

            // Check whether this new alert can be grouped with the previous one
            // (Same region + 15 second cutoff threshold in either direction)
            if (lastAlert != null && lastAlert.zone.equals(currentAlert.zone) && currentAlert.date >= lastAlert.date - 15 && currentAlert.date <= lastAlert.date + 15) {
                // Group with previous alert list item
                lastAlert.localizedCity += ", " + currentAlert.localizedCity;
                lastAlert.groupedCities.add(currentAlert.city);
            }
            else {
                // New alert (not grouped with previous item)
                groupedAlerts.add(currentAlert);
                lastAlert = currentAlert;
            }
        }

        // Hooray
        return groupedAlerts;
    }

    private String checkForUpdates() {
        // Grab the update JSON
        String updateJson;

        try {
            // Get it from /update/android
            updateJson = HTTP.get("/update/android");
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Get update info failed", exc);

            // Return error code
            return null;
        }

        // Convert into JSON
        VersionInfo updateInfo;

        try {
            // Convert JSON to object
            updateInfo = Singleton.getJackson().readValue(updateJson, com.red.alert.model.res.VersionInfo.class);
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Parsing update info failed", exc);

            // Stop execution
            return null;
        }

        // Don't show update dialog?
        if (!updateInfo.showDialog) {
            return null;
        }

        // Update available? (Higher version code)
        if (updateInfo.versionCode > AppVersion.getVersionCode(this)) {
            // Return newer version string for display
            return updateInfo.version;
        }

        // No need to update
        return null;
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

        // Avoid hiding invalid dialogs
        mIsDestroyed = true;
    }

    void invalidateAlertList() {
        // Clear global list
        mDisplayAlerts.clear();

        // Add all the new alerts
        mDisplayAlerts.addAll(mNewAlerts);

        // Invalidate the list
        mAlertsAdapter.notifyDataSetChanged();

        // No alerts? Show default
        if (mDisplayAlerts.size() == 0) {
            mNoAlerts.setVisibility(View.VISIBLE);
        }
        else {
            mNoAlerts.setVisibility(View.GONE);
        }
    }

    void showRegistrationSuccessDialog() {
        // Already displayed?
        if (AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Build the dialog
        AlertDialogBuilder.showGenericDialog(getString(R.string.pushRegistrationSuccess), getString(R.string.pushRegistrationSuccessDesc), getString(R.string.okay), null, false, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Start settings activity
                goToSettings(true);
            }
        });

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
            if (isFinishing() || mIsDestroyed) {
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
            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(Main.this);

            // Prevent cancel
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.signing_up));

            // First time registering?
            if (!FCMRegistration.isRegistered(Main.this) || !PushyRegistration.isRegistered(Main.this)) {
                // Show the progress dialog
                mLoading.show();
            }
        }

        @Override
        protected Exception doInBackground(Integer... Parameter) {
            // Make sure we have Google Play Services installed
            if (!GooglePlayServices.isAvailable(Main.this)) {
                return new Exception("This app requires Google Play Services.");
            }

            // Keep track of previously-saved Pushy & FCM tokens to detect changes and update the API
            String previousPushyToken = PushyRegistration.getRegistrationToken(Main.this);
            String previousFirebaseToken = FCMRegistration.getRegistrationToken(Main.this);

            try {
                // Register for Pushy push notifications
                String pushyToken = PushyRegistration.registerForPushNotifications(Main.this);

                // Register for FCM push notifications
                String fcmToken = FCMRegistration.registerForPushNotifications(Main.this);

                // First time registering with the API?
                if (!RedAlertAPI.isRegistered(Main.this)) {
                    // Register with RedAlert API and store user ID & hash
                    RedAlertAPI.register(fcmToken, pushyToken,Main.this);
                }
                else {
                    // FCM or Pushy token have changed?
                    if (!previousFirebaseToken.equals(fcmToken) ||
                    !previousPushyToken.equals(pushyToken)) {
                        // Update token server-side
                        RedAlertAPI.updatePushTokens(fcmToken, pushyToken, Main.this);
                    }
                }

                // First time subscribing with the API?
                if (!RedAlertAPI.isSubscribed(Main.this)) {
                    // Update notification preferences
                    RedAlertAPI.updateNotificationPreferences(Main.this);

                    // Subscribe for alerts based on current city/region selections
                    RedAlertAPI.subscribe(Main.this);

                    // Update Pub/Sub subscriptions
                    PushManager.updateSubscriptions(Main.this);
                }
            }
            catch (Exception exc) {
                return exc;
            }

            // Success
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            // Activity dead?
            if (isFinishing() || mIsDestroyed) {
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
                String errorMessage = getString(R.string.pushRegistrationFailed) + "\n\n" + exc.getMessage() + "\n\n" + exc.getCause();

                // Build the dialog
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, Main.this, null);
            }
            else {
                // Success, show success dialog if haven't yet
                showRegistrationSuccessDialog();
            }
        }
    }

    public class CheckForUpdatesAsync extends AsyncTaskAdapter<Integer, String, String> {
        @Override
        protected String doInBackground(Integer... Parameter) {
            // Try to check for updates
            return checkForUpdates();
        }

        @Override
        protected void onPostExecute(String newVersion) {
            // Activity dead?
            if (isFinishing() || mIsDestroyed) {
                return;
            }

            // Got a return code?
            if (!StringUtils.stringIsNullOrEmpty(newVersion)) {
                // Show update dialog
                UpdateDialogs.showUpdateDialog(Main.this, newVersion);
            }
        }
    }
}
