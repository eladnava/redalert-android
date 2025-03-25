package com.red.alert.activities.settings;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;
import me.pushy.sdk.Pushy;

import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.red.alert.R;
import com.red.alert.config.API;
import com.red.alert.config.Donations;
import com.red.alert.config.Logging;
import com.red.alert.config.Support;
import com.red.alert.config.Testing;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.logic.push.PushManager;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.req.SelfTestRequest;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.compatibility.ProgressDialogCompat;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.os.AndroidSettings;
import com.red.alert.utils.marketing.GooglePlay;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class General extends AppCompatPreferenceActivity {
    boolean mIsTesting;
    boolean mFcmTestPassed;
    boolean mPushyTestPassed;

    String mPreviousZones;
    String mPreviousCities;

    Preference mRate;
    Preference mDonate;
    Preference mWebsite;
    Preference mContact;
    Preference mAdvanced;
    Preference mTestAlert;
    Preference mBatteryOptimization;
    CheckBoxPreference mNotificationsEnabled;

    PreferenceCategory mMainCategory;
    PreferenceCategory mBatteryOptimizationCategory;

    ListPreference mThemeSelection;
    ListPreference mLanguageSelection;

    BillingClient mBillingClient;

    SearchableMultiSelectPreference mCitySelection;
    SearchableMultiSelectPreference mZoneSelection;
    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(LocationSelectionEvents.LOCATIONS_UPDATED)) {
                // Refresh city/region descriptions
                refreshAreaValues();

                // Update subscriptions on the server-side
                new UpdateSubscriptionsAsync().execute();
            }

            // FCM test passed?
            if (Key.equalsIgnoreCase(SelfTestEvents.FCM_TEST_PASSED)) {
                mFcmTestPassed = true;
            }

            // Pushy test passed?
            if (Key.equalsIgnoreCase(SelfTestEvents.PUSHY_TEST_PASSED)) {
                mPushyTestPassed = true;
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

        // Handle extra parameters
        handleIntentExtras();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    void handleIntentExtras() {
        // Get extras
        Bundle extras = getIntent().getExtras();

        // None sent?
        if (extras == null) {
            return;
        }

        // Get city selection boolean
        boolean showCitySelection = extras.getBoolean(SettingsEvents.SHOW_CITY_SELECTION);

        // Show selection?
        if (showCitySelection) {
            mCitySelection.showDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Clear notifications and stop any playing sounds
        AppNotifications.clearAll(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);

        // Get power manager instance
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Android M (6) and up only
        // Check if app is already whitelisted from battery optimizations
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            // Remove reminder message about whitelisting the app
            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference(getResources().getString(R.string.mainSettingsScreen));
            preferenceScreen.removePreference(mBatteryOptimizationCategory);
        }
    }

    void selfTestCompleted(int result) {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Use builder to create dialog
        builder.setTitle(getString(R.string.test)).setMessage(getString(result)).setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int id) {
                // Stop siren (and clear notifications)
                AppNotifications.clearAll(General.this);

                // Close dialog
                dialogInterface.dismiss();
            }
        });

        // Avoid cancellation
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface Dialog) {
                // Stop siren (and clear notifications)
                AppNotifications.clearAll(General.this);
            }
        });

        try {
            // Build it
            Dialog dialog = builder.create();

            // Show it
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, this);
        }
        catch (Exception exc) {
            // Show toast instead
            Toast.makeText(this, getString(result), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI() {
        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Load settings from XML (there is no non-deprecated way to do it on API level 7)
        addPreferencesFromResource(R.xml.settings);

        // Cache resource IDs
        mRate = findPreference(getString(R.string.ratePref));
        mDonate = findPreference(getString(R.string.donatePref));
        mWebsite = findPreference(getString(R.string.websitePref));
        mContact = findPreference(getString(R.string.contactPref));
        mAdvanced = findPreference(getString(R.string.advancedPref));
        mTestAlert = findPreference(getString(R.string.selfTestPref));
        mBatteryOptimization = findPreference(getString(R.string.batteryOptimizationPref));
        mMainCategory = (PreferenceCategory) findPreference(getString(R.string.mainCategoryPref));
        mBatteryOptimizationCategory = (PreferenceCategory)findPreference(getString(R.string.batteryOptimizationCategory));
        mCitySelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedCitiesPref)));
        mZoneSelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedZonesPref)));
        mThemeSelection = (ListPreference) findPreference(getString(R.string.themePref));
        mLanguageSelection = (ListPreference) findPreference(getString(R.string.langPref));
        mNotificationsEnabled = (CheckBoxPreference)findPreference(getString(R.string.enabledPref));

        // Populate setting values
        initializeSettings();

        // Set up listeners
        initializeListeners();
    }

    void initializeSettings() {
        // Set entries & values
        mZoneSelection.setEntries(getResources().getStringArray(R.array.zoneNames));
        mZoneSelection.setEntryValues(getResources().getStringArray(R.array.zoneValues));

        // Set entries & values
        mCitySelection.setEntries(LocationData.getAllCityNames(this));
        mCitySelection.setEntryValues(LocationData.getAllCityValues(this));

        // Not registered yet?
        if (!RedAlertAPI.isRegistered(this)) {
            mNotificationsEnabled.setEnabled(false);
        }

        // Refresh area values
        refreshAreaValues();
    }

    void refreshAreaValues() {
        // Get selected cities
        String selectedCities = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedCitiesPref), getString(R.string.none));

        // Update summary text
        mCitySelection.setSummary(getString(R.string.selectedCitiesDesc) + "\r\n(" + LocationData.getSelectedCityNamesByValues(this, selectedCities, mCitySelection.getEntries(), mCitySelection.getEntryValues()) + ")");

        // Get selected zones
        String selectedZones = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedZonesPref), getString(R.string.none));

        // Update summary text
        mZoneSelection.setSummary(getString(R.string.selectedZonesDesc) + "\r\n(" + LocationData.getSelectedCityNamesByValues(this, selectedZones, mZoneSelection.getEntries(), mZoneSelection.getEntryValues()) + ")");

        // Save in case the update subscriptions request fails
        if (mPreviousZones == null && mPreviousCities == null) {
            mPreviousZones = selectedZones;
            mPreviousCities = selectedCities;
        }
    }

    void initializeListeners() {
        // Language preference select
        mLanguageSelection.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                // Automatic selected?
                if (o.equals("")) {
                    // Restore default phone locale
                    Localization.restoreDefaultLocale(General.this);
                }

                // Close settings activity
                finish();

                // Notify language changed
                Broadcasts.publish(General.this, SettingsEvents.THEME_OR_LANGUAGE_CHANGED);

                // Update the preference
                return true;
            }
        });

        // Theme selection
        mThemeSelection.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, final Object value) {
                // Close settings activity
                finish();

                // Notify theme changed
                Broadcasts.publish(General.this, SettingsEvents.THEME_OR_LANGUAGE_CHANGED);

                // Update the preference
                return true;
            }
        });

        // Test push
        mTestAlert.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Not already testing?
                if (!mIsTesting) {
                    // Send a test push
                    new PerformSelfTestAsync().execute();
                }

                // Consume event
                return true;
            }
        });

        // Battery optimization whitelist
        mBatteryOptimization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Show a dialog to the user
                AlertDialogBuilder.showGenericDialog(getString(R.string.disableBatteryOptimizations), AndroidSettings.getBatteryOptimizationWhitelistInstructions(General.this), getString(R.string.okay), getString(R.string.notNow), true, General.this, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        // Clicked okay?
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            // Open App Info settings page
                            AndroidSettings.openAppInfoPage(General.this);
                        }
                    }
                });

                // Consume event
                return true;
            }
        });

        // Website button
        mWebsite.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Initialize browser intent
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(Support.WEBSITE_LINK));

                try {
                    // Open browser
                    startActivity(browser);
                }
                catch (ActivityNotFoundException ex) {
                    // Do nothing
                }

                // Consume event
                return true;
            }
        });

        // Notification toggle listener
        mNotificationsEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Update notification preferences on the server-side
                new UpdateNotificationsAsync().execute();

                // Enable/disable Pushy service according to new value
                Pushy.toggleNotifications((boolean)newValue, General.this);

                // Tell Android to persist new checkbox value
                return true;
            }
        });

        // Rate button
        mRate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Open app page
                GooglePlay.openAppListingPage(General.this);

                // Consume event
                return true;
            }
        });

        // Donate button
        mDonate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Show popup dialog with instructions and possible donation amounts
                showDonationDialog();

                // Consume event
                return true;
            }
        });

        // Set up contact click listener
        mContact.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Add debug flags
                String body = getContactEmailBody();

                // Set up contact click listener
                Intent selectorIntent = new Intent(Intent.ACTION_SENDTO);
                selectorIntent.setData(Uri.parse("mailto:"));

                // Prepare e-mail intent
                final Intent emailIntent = new Intent(Intent.ACTION_SEND);

                // Add e-mail, subject & debug body
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{Support.CONTACT_EMAIL});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.appName));
                emailIntent.putExtra(Intent.EXTRA_TEXT, body);

                // Limit to mail apps only
                emailIntent.setSelector(selectorIntent);

                try {
                    // Try to open user's e-mail app
                    startActivity(Intent.createChooser(emailIntent, getString(R.string.contact)));
                }
                catch (ActivityNotFoundException exc) {
                    // Show a toast instead
                    Toast.makeText(General.this, R.string.manualContact, Toast.LENGTH_LONG).show();
                }

                // Consume event
                return true;
            }
        });

        // Set up advanced settings listener
        mAdvanced.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent advanced = new Intent();

                // Set advanced class
                advanced.setClass(General.this, Advanced.class);

                // Show advanced settings
                startActivity(advanced);

                // Consume event
                return true;
            }
        });
    }

    String getContactEmailBody() {
        // Add sent via and app version
        String body = getString(R.string.sentVia) + " " + getString(R.string.appName) + " " + AppVersion.getVersionName(General.this);

        // Break 2 lines
        body += "\r\n\r\n";

        // Add selected cities
        body += getString(R.string.selectedCities) + ": " + LocationData.getSelectedCityNamesByValues(this, Singleton.getSharedPreferences(this).getString(getString(R.string.selectedCitiesPref), ""), LocationData.getAllCityNames(this), LocationData.getAllCityValues(this));

        // Break 1 line
        body += "\r\n";

        // Add selected zones
        body += getString(R.string.selectedZones) + ": " + LocationData.getSelectedCityNamesByValues(this, Singleton.getSharedPreferences(this).getString(getString(R.string.selectedZonesPref), ""), getResources().getStringArray(R.array.zoneNames), getResources().getStringArray(R.array.zoneValues));

        // Break 2 lines
        body += "\r\n\r\n";

        // Add selected secondary cities
        body += getString(R.string.selectedSecondaryCities) + ": " + LocationData.getSelectedCityNamesByValues(this, Singleton.getSharedPreferences(this).getString(getString(R.string.selectedSecondaryCitiesPref), ""), LocationData.getAllCityNames(this), LocationData.getAllCityValues(this));

        // Break 2 lines
        body += "\r\n\r\n";

        // Add debug info
        body += getString(R.string.debugInfo) + ": ";

        // Add setting values & push notification device information
        body += "user.id=" + RedAlertAPI.getUserId(this) + ", ";
        body += "user.hash=" + RedAlertAPI.getUserHash(this) + ", ";
        body += "primary.enabled=" + AppPreferences.getNotificationsEnabled(this) + ", ";
        body += "secondary.enabled=" + AppPreferences.getSecondaryNotificationsEnabled(this) + ", ";
        body += "location.enabled=" + AppPreferences.getLocationAlertsEnabled(this) + ", ";

        // Check if location alerts enabled
        if (AppPreferences.getLocationAlertsEnabled(this)) {
            // Get current location
            Location location = LocationLogic.getCurrentLocation(this);

            // Null check
            if (location != null) {
                // Add nearby cities for debugging purposes
                body += "location.nearbyCities=" + LocationData.getNearbyCityNames(location, this) + ", ";
            }

            // Add max distance and update interval
            body += "location.maxDistance=" + LocationLogic.getMaxDistanceKilometers(this, -1) + "km, ";
            body += "location.updateInterval=every " + LocationLogic.getUpdateIntervalMilliseconds(this) / 1000 / 60 + " minute(s), ";
        }

        // Add other params
        body += "volume.primary=" + AppPreferences.getPrimaryAlertVolume(this, -1) + ", ";
        body += "volume.secondary=" + AppPreferences.getSecondaryAlertVolume(this, -1) + ", ";
        body += "sound.primary=" + SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, null, this) + ", ";
        body += "sound.secondary=" + SoundLogic.getAlertSoundName(AlertTypes.SECONDARY, null, this) + ", ";
        body += "popup.enabled=" + AppPreferences.getPopupEnabled(this) + ", ";
        body += "secondaryPopup.enabled=" + AppPreferences.getSecondaryPopupEnabled(this) + ", ";
        body += "wakeScreen.enabled=" + AppPreferences.getWakeScreenEnabled(this) + ", ";
        body += "fcm=" + FCMRegistration.isRegistered(this) + ", ";
        body += "fcm.token=" + FCMRegistration.getRegistrationToken(this) + ", ";
        body += "pushy=" + PushyRegistration.isRegistered(this) + ", ";
        body += "pushy.token=" + PushyRegistration.getRegistrationToken(this) + ", ";
        body += "pushy.isConnected=" + Pushy.isConnected() + ", ";
        body += "pushy.foregroundServiceEnabled=" + AppPreferences.getForegroundServiceEnabled(this) + ", ";
        body += "android.sdk=" + Build.VERSION.SDK_INT + ", ";
        body += "android.version=" + Build.VERSION.RELEASE + ", ";

        // Add battery optimizations whitelist status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            body += "android.isIgnoringBatteryOptimizations=" + ((PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()) + ", ";
        }

        // Add schedule exact alarms permission status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            body += "android.canScheduleExactAlarms=" + ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms() + ", ";
        }

        body += "phone.manufacturer=" + Build.MANUFACTURER + ", ";
        body += "phone.model=" + Build.MODEL;

        // Break 2 lines
        body += "\r\n\r\n";

        // Add default problem description text
        body += getString(R.string.problemDesc);

        // Break a few lines
        body += "\r\n";

        // Return body
        return body;
    }

    void sendTestPushViaPushy() throws Exception {
        // Log to logcat
        Log.d(Logging.TAG, "Testing Pushy...");

        // Reset test flag
        mPushyTestPassed = false;

        // Not registered?
        if (!PushyRegistration.isRegistered(this)) {
            // No need for localization - only shows up in logcat for now
            throw new Exception("The device is not registered for push notifications via Pushy.");
        }

        // Grab registration token
        String token = PushyRegistration.getRegistrationToken(this);

        // Get user's locale
        String locale = getResources().getConfiguration().locale.getLanguage();

        // Create registration request
        SelfTestRequest test = new SelfTestRequest(token, locale, API.PUSHY_PLATFORM_IDENTIFIER);

        // Send the request to our API
        HTTP.post("/test", Singleton.getJackson().writeValueAsString(test));
    }

    void sendTestPushViaFcm() throws Exception {
        // Log to logcat
        Log.d(Logging.TAG, "Testing FCM...");

        // Reset test flag
        mFcmTestPassed = false;

        // Not registered?
        if (!FCMRegistration.isRegistered(this)) {
            // No need for localization - only shows up in logcat for now
            throw new Exception("The device is not registered for push notifications via FCM.");
        }

        // Grab registration token
        String token = FCMRegistration.getRegistrationToken(this);

        // Get user's locale
        String locale = getResources().getConfiguration().locale.getLanguage();

        // Create registration request
        SelfTestRequest test = new SelfTestRequest(token, locale, API.PLATFORM_IDENTIFIER);

        // Send the request to our API
        HTTP.post("/test", Singleton.getJackson().writeValueAsString(test));
    }

    boolean didPassFcmTest() {
        // Calculate the max timestamp
        long maxTimestamp = System.currentTimeMillis() + Testing.PUSH_GATEWAY_TIMEOUT_SECONDS * 1000;

        // Wait until boolean value changes or enough time passes
        while (!mFcmTestPassed && System.currentTimeMillis() < maxTimestamp) {
            // Sleep to relieve the thread
            try {
                Thread.sleep(100);
            }
            catch (Exception exc) {
            }
        }

        // Return the outcome of the test
        return mFcmTestPassed;
    }

    boolean didPassPushyTest() {
        // Calculate the max timestamp
        long maxTimestamp = System.currentTimeMillis() + Testing.PUSH_GATEWAY_TIMEOUT_SECONDS * 1000;

        // Wait until boolean value changes or enough time passes
        while (!mPushyTestPassed && System.currentTimeMillis() < maxTimestamp) {
            // Sleep to relieve the thread
            try {
                Thread.sleep(100);
            }
            catch (Exception exc) {
            }
        }

        // Return the outcome of the test
        return mPushyTestPassed;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu) {
        // Add settings button
        initializeShareButton(OptionsMenu);

        // Show the menu
        return true;
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add share button
        MenuItem shareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.share));

        // Set share icon
        shareItem.setIcon(R.drawable.ic_share);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(shareItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, open share
        shareItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Prepare share intent
                Intent shareIntent = new Intent(Intent.ACTION_SEND);

                // Set as text/plain
                shareIntent.setType("text/plain");

                // Add text
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage));

                // Show chooser
                startActivity(Intent.createChooser(shareIntent, getString(R.string.shareDesc)));

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

    public class PerformSelfTestAsync extends AsyncTaskAdapter<Integer, String, Integer> {
        ProgressDialog mLoading;

        public PerformSelfTestAsync() {
            // Prevent concurrent testing
            mIsTesting = true;

            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(General.this);

            // Prevent cancel
            mLoading.setCancelable(false);

            // Set default message
            mLoading.setMessage(getString(R.string.loading));

            // Show the progress dialog
            mLoading.show();
        }

        @Override
        protected void onProgressUpdate(String... value) {
            super.onProgressUpdate(value);

            // Update progress dialog
            mLoading.setMessage(value[0]);
        }

        @Override
        protected Integer doInBackground(Integer... Parameter) {
            // Test results
            boolean fcmFailed = false, pushyFailed = false;

            // IsTesting FCM
            publishProgress(getString(R.string.testingFCM));

            try {
                // Send a test push
                sendTestPushViaFcm();
            }
            catch (Exception exc) {
                // FCM test is done
                fcmFailed = true;

                // Log to console
                Log.e(Logging.TAG, "FCM test failed", exc);
            }

            // Wait for test push to arrive
            if (!fcmFailed) {
                if (!didPassFcmTest()) {
                    fcmFailed = true;
                }
            }

            // Done with FCM, testing Pushy
            publishProgress(getString(R.string.testingPushy));

            try {
                // Send a test push
                sendTestPushViaPushy();
            }
            catch (Exception exc) {
                // We failed
                pushyFailed = true;

                // Log to console
                Log.e(Logging.TAG, "Pushy test failed", exc);
            }

            // Wait for test push to arrive
            if (!pushyFailed) {
                if (!didPassPushyTest()) {
                    pushyFailed = true;
                }
            }

            // At least one passed?
            if (!fcmFailed || !pushyFailed) {
                // Display "successful test" message
                AlertLogic.processIncomingAlert(AlertTypes.TEST, getString(R.string.testSuccessful), AlertTypes.TEST, null, General.this);
            }

            // Both succeeded?
            if (!fcmFailed && !pushyFailed) {
                return R.string.testSuccessfulLong;
            }

            // Both failed?
            else if (fcmFailed && pushyFailed) {
                return R.string.testFailed;
            }

            // Only FCM failed?
            else if (fcmFailed) {
                return R.string.fcmTestFailed;
            }

            // Only Pushy failed?
            else if (pushyFailed) {
                return R.string.pushyTestFailed;
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            // No longer testing
            mIsTesting = false;

            // Activity dead?
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // Hide progress dialog
            if (mLoading.isShowing()) {
                mLoading.dismiss();
            }

            // Show result dialog
            selfTestCompleted(result);
        }
    }

    public class UpdateSubscriptionsAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        ProgressDialog mLoading;

        public UpdateSubscriptionsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(General.this);

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
                PushManager.updateSubscriptions(General.this);

                // Update alert subscriptions
                RedAlertAPI.subscribe(General.this);
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

                // Restore previous city/region selections
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(General.this).edit();

                // Restore original values
                editor.putString(getString(R.string.selectedZonesPref), mPreviousZones);
                editor.putString(getString(R.string.selectedCitiesPref), mPreviousCities);

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
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, General.this, null);
            }
            else {
                // Clear previously cached values
                mPreviousZones = null;
                mPreviousCities = null;
            }

            // Refresh city/region setting values
            refreshAreaValues();
        }
    }

    public class UpdateNotificationsAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        ProgressDialog mLoading;

        public UpdateNotificationsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = ProgressDialogCompat.getStyledProgressDialog(General.this);

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
                PushManager.updateSubscriptions(General.this);

                // Update notification preferences
                RedAlertAPI.updateNotificationPreferences(General.this);
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
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(General.this).edit();

                // Restore original values
                editor.putBoolean(getString(R.string.enabledPref), !AppPreferences.getNotificationsEnabled(General.this));

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
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay), null, false, General.this, null);
            }

            // Refresh checkbox with new value
            mNotificationsEnabled.setChecked(AppPreferences.getNotificationsEnabled(General.this));
        }
    }


    void showDonationDialog() {
        // Only initialize one billing client
        if (mBillingClient != null && mBillingClient.isReady()) {
            showProducts();
            return;
        }

        // Wait for existing client to finish initialization
        else if (mBillingClient != null) {
            return;
        }

        // Enable one time product display
        PendingPurchasesParams params = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        // Create billing client
        mBillingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases(params)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
                        // Successful purchase?
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                            // Traverse all successful purchases
                            for (Purchase purchase : list) {
                                // Item state is purchased?
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    // Consume the purchase (so same item can be repurchased in the future) to acknowledge it
                                    consumePurchase(purchase);
                                }
                            }

                            // Show thanks dialog
                            AlertDialogBuilder.showGenericDialog(getString(R.string.donateSuccess), getString(R.string.donateSuccessDesc), getString(R.string.okay), null, false, General.this, null);
                        }
                    }
                }).build();

        // Start the connection after initializing the billing client
        establishConnection();
    }

    private void consumePurchase(Purchase purchase) {
        // Params to consume the purchased consumable item by passing in the purchase token
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        // Consume item
        mBillingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                // Success?
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // The consumable was successfully consumed, so the user can now repurchase it
                    Log.d(Logging.TAG, "Donation consumed successfully!");
                } else {
                    // Log any errors when consuming the product
                    Log.d(Logging.TAG, "Error consuming donation: " + billingResult.getDebugMessage());
                }
            }
        });
    }
    void establishConnection() {
        // Connect billing client
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                // Connection success
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // Query for available products
                    showProducts();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to Google Play
                establishConnection();
            }
        });
    }

    void showProducts() {
        // Prepare ArrayList of products to query for
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();

        // Traverse product IDs
        for (String productId : Donations.DONATION_PRODUCT_IDS) {
            // Create product objects with ID populated
            productList.add(
                    QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
            );
        }

        // Prepare product query params with product list
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        // Query product details asynchronously
        mBillingClient.queryProductDetailsAsync(params, (billingResult, prodDetailsList) -> {
            // Success?
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                // Sort list of products by price ASC
                Collections.sort(prodDetailsList, new Comparator<ProductDetails>() {
                    @Override
                    public int compare(ProductDetails p1, ProductDetails p2) {
                        // Compare prices
                        return Double.compare(p1.getOneTimePurchaseOfferDetails().getPriceAmountMicros(), p2.getOneTimePurchaseOfferDetails().getPriceAmountMicros());
                    }
                });

                // Show dialog with sorted products
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Activity still running?
                        if (!isFinishing()) {
                            // Show dialog
                            showProductDialog(prodDetailsList);
                        }
                    }
                });
            }
        });
    }

    private void showProductDialog(List<ProductDetails> productList) {
        // Prepare AlertDialog with items list
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Set dialgo title
        builder.setTitle(R.string.donate);

        // Convert ProductDetails to CharSequence Array (with an additional item for the instructions)
        CharSequence[] items = new CharSequence[productList.size() + 1];

        // Add dialog instructions (instruct user to select product)
        items[0] = getString(R.string.donateInstructions);

        // Traverse products list
        for (int i = 0; i < productList.size(); i++) {
            // Get product name
            String name = productList.get(i).getName();

            // Localize product name based on product ID
            if (productList.get(i).getProductId().equals("donation_10")) {
                name = getString(R.string.donation_10);
            }
            else if (productList.get(i).getProductId().equals("donation_20")) {
                name = getString(R.string.donation_20);
            }
            else if (productList.get(i).getProductId().equals("donation_50")) {
                name = getString(R.string.donation_50);
            }
            else if (productList.get(i).getProductId().equals("donation_100")) {
                name = getString(R.string.donation_100);
            }
            else if (productList.get(i).getProductId().equals("donation_200")) {
                name = getString(R.string.donation_200);
            }

            // Set item value to localized item name
            items[i + 1] = name;
        }

        // Set AlertDialog items list
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Ignore instructions click event
                if (which == 0) {
                    return;
                }

                // Get selected product based on index of tapped product
                ProductDetails selectedProduct = productList.get(which - 1);

                // Launch Google Play purchase flow
                launchPurchaseFlow(selectedProduct);
            }
        });

        // Allow canceling the dialog
        builder.setNegativeButton(R.string.notNow, null);

        // Build dialog
        Dialog dialog = builder.create();

        // Show dialog
        dialog.show();

        // Support for RTL languages
        RTLSupport.mirrorDialog(dialog, this);
    }

    private void launchPurchaseFlow(ProductDetails product) {
        // Prepare billing flow params by passing in product object
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                        Collections.singletonList(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(product)
                                        .build()))
                .build();

        // Let Google Play handle the billing flow
        mBillingClient.launchBillingFlow(this, params);
    }
}
