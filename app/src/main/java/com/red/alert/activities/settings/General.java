package com.red.alert.activities.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import androidx.core.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.config.API;
import com.red.alert.config.Lifeshield;
import com.red.alert.config.Logging;
import com.red.alert.config.Support;
import com.red.alert.config.Testing;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.logic.integration.BluetoothIntegration;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.req.SelfTestRequest;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.compatibility.ProgressDialogCompat;
import com.red.alert.ui.dialogs.custom.BluetoothDialogs;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.marketing.GooglePlay;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.threading.AsyncTaskAdapter;

public class General extends AppCompatPreferenceActivity {
    boolean mIsTesting;
    boolean mIsDestroyed;
    boolean mFcmTestPassed;
    boolean mPushyTestPassed;

    MenuItem mLoadingItem;

    Preference mRate;
    Preference mWebsite;
    Preference mContact;
    Preference mAdvanced;
    Preference mTestAlert;
    Preference mLifeshield;

    PreferenceCategory mMainCategory;
    ListPreference mLanguageSelection;

    SearchableMultiSelectPreference mCitySelection;
    SearchableMultiSelectPreference mZoneSelection;
    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(LocationSelectionEvents.REFRESH_AREA_VALUES)) {
                refreshAreaValues();
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

        // Get zone selection boolean
        boolean showZoneSelection = extras.getBoolean(SettingsEvents.SHOW_ZONE_SELECTION);

        // Show selection?
        if (showZoneSelection) {
            mZoneSelection.showDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Avoid hiding invalid dialogs
        mIsDestroyed = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Clear notifications and stop any playing sounds
        AppNotifications.clearAll(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);
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

        // Load settings from XML There is no non-deprecated way to do it on API Level 7
        addPreferencesFromResource(R.xml.settings);

        // Cache resource IDs
        mRate = findPreference(getString(R.string.ratePref));
        mWebsite = findPreference(getString(R.string.websitePref));
        mContact = findPreference(getString(R.string.contactPref));
        mAdvanced = findPreference(getString(R.string.advancedPref));
        mTestAlert = findPreference(getString(R.string.selfTestPref));
        mLifeshield = findPreference(getString(R.string.lifeshieldPref));
        mMainCategory = (PreferenceCategory) findPreference(getString(R.string.mainCategoryPref));
        mCitySelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedCitiesPref)));
        mZoneSelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedZonesPref)));
        mLanguageSelection = (ListPreference) findPreference(getString(R.string.langPref));

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

        // Refresh area values
        refreshAreaValues();
    }

    void refreshAreaValues() {
        // Get selected cities
        String selectedCities = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedCitiesPref), getString(R.string.none));

        // Update summary text
        mCitySelection.setSummary(getString(R.string.cityDesc) + "\r\n(" + LocationData.getSelectedCityNamesByValues(this, selectedCities, mCitySelection.getEntries(), mCitySelection.getEntryValues()) + ")");

        // Get selected zones
        String selectedZones = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedZonesPref), getString(R.string.none));

        // Update summary text
        mZoneSelection.setSummary(getString(R.string.zonesDesc) + "\r\n(" + LocationData.getSelectedCityNamesByValues(this, selectedZones, mZoneSelection.getEntries(), mZoneSelection.getEntryValues()) + ")");
    }

    void initializeListeners() {
        // Language preference select
        mLanguageSelection.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                // Show instructions toast
                Toast.makeText(General.this, R.string.langSelectionSuccess, Toast.LENGTH_LONG).show();

                // Update the preference
                return true;
            }
        });

        // Test push
        mTestAlert.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Did we enable a device integration but Bluetooth is disabled?
                if (BluetoothIntegration.isIntegrationEnabled(General.this) && !BluetoothIntegration.isBluetoothEnabled()) {
                    // Ask user politely to enable Bluetooth
                    BluetoothDialogs.showEnableBluetoothDialog(General.this);

                    // Don't run the test yet
                    return false;
                }

                // Not already testing?
                if (!mIsTesting) {
                    // Send a test push
                    new PerformSelfTestAsync().execute();
                }

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

        // Lifeshield website button
        mLifeshield.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Initialize browser intent
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(Lifeshield.WEBSITE_LINK));

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

        // Set up contact click listener
        mContact.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Set up contact click listener
                Intent contactIntent = new Intent(Intent.ACTION_SEND);

                // Set e-mail content type
                contactIntent.setType("message/rfc822");

                // Add debug flags
                String body = getContactEmailBody();

                // Set recipient and subject
                contactIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{Support.CONTACT_EMAIL});
                contactIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.appName));
                contactIntent.putExtra(Intent.EXTRA_TEXT, body);

                try {
                    // Try sending via an e-mail app
                    startActivity(Intent.createChooser(contactIntent, getString(R.string.contact)));
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
        // Add default problem description text
        String body = getString(R.string.problemDesc);

        // Break a few lines
        body += "\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n";

        // Add sent via and app version
        body += getString(R.string.sentVia) + " " + getString(R.string.appName) + " " + AppVersion.getVersionName(General.this);

        // Break 2 lines
        body += "\r\n\r\n";

        // Add selected zones
        body += getString(R.string.selectedZones) + ": " + LocationData.getSelectedCityNamesByValues(this, Singleton.getSharedPreferences(this).getString(getString(R.string.selectedZonesPref), ""), getResources().getStringArray(R.array.zoneNames), getResources().getStringArray(R.array.zoneValues));

        // Break 1 line
        body += "\r\n";

        // Add selected cities
        body += getString(R.string.selectedCities) + ": " + LocationData.getSelectedCityNamesByValues(this, Singleton.getSharedPreferences(this).getString(getString(R.string.selectedCitiesPref), ""), LocationData.getAllCityNames(this), LocationData.getAllCityValues(this));

        // Break 2 lines
        body += "\r\n\r\n";

        // Add debug info
        body += getString(R.string.debugInfo) + ": ";

        // Add setting values & push notification device information
        body += "primary.enabled=" + AppPreferences.getNotificationsEnabled(this) + ", ";
        body += "secondary.enabled=" + AppPreferences.getNotificationsEnabled(this) + ", ";
        body += "location.enabled=" + AppPreferences.getLocationAlertsEnabled(this) + ", ";
        body += "volume.primary=" + AppPreferences.getPrimaryAlertVolume(this, -1) + ", ";
        body += "volume.secondary=" + AppPreferences.getSecondaryAlertVolume(this, -1) + ", ";
        body += "fcm=" + FCMRegistration.isRegistered(this) + ", ";
        body += "fcm.token=" + FCMRegistration.getRegistrationToken(this) + ", ";
        body += "pushy=" + PushyRegistration.isRegistered(this) + ", ";
        body += "pushy.token=" + PushyRegistration.getRegistrationToken(this) + ", ";
        body += "android.sdk=" + Build.VERSION.SDK_INT + ", ";
        body += "android.version=" + Build.VERSION.RELEASE + ", ";
        body += "phone.manufacturer=" + Build.MANUFACTURER + ", ";
        body += "phone.model=" + Build.MODEL;

        // Break 2 lines
        body += "\r\n\r\n";

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
        HTTP.post(API.API_ENDPOINT + "/test", Singleton.getJackson().writeValueAsString(test));
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
        HTTP.post(API.API_ENDPOINT + "/test", Singleton.getJackson().writeValueAsString(test));
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

    void toggleProgressBarVisibility(boolean visibility) {
        // Set loading visibility
        if (mLoadingItem != null) {
            mLoadingItem.setVisible(visibility);
        }
    }

    void initializeLoadingIndicator(Menu OptionsMenu) {
        // Add refresh in Action Bar
        mLoadingItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.signing_up));

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
        initializeShareButton(OptionsMenu);

        // Show the menu
        return true;
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add share button
        MenuItem shareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.share));

        // Set up the view
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
                AlertLogic.processIncomingAlert(getString(R.string.testSuccessful), AlertTypes.TEST, General.this);
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
            if (isFinishing() || mIsDestroyed) {
                return;
            }

            // Hide progress dialog
            if (mLoading.isShowing()) {
                mLoading.dismiss();
            }

            // Hide loading indicator
            toggleProgressBarVisibility(false);

            // Show result dialog
            selfTestCompleted(result);
        }
    }
}
