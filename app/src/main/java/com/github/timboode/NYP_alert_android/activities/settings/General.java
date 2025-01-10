package com.github.timboode.NYP_alert_android.activities.settings;

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

import androidx.core.view.MenuItemCompat;

import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.config.Support;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.LocationSelectionEvents;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.SettingsEvents;
import com.github.timboode.NYP_alert_android.logic.feedback.sound.SoundLogic;
import com.github.timboode.NYP_alert_android.logic.push.PushManager;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;
import com.github.timboode.NYP_alert_android.ui.activities.AppCompatPreferenceActivity;
import com.github.timboode.NYP_alert_android.ui.compatibility.ProgressDialogCompat;
import com.github.timboode.NYP_alert_android.ui.dialogs.AlertDialogBuilder;
import com.github.timboode.NYP_alert_android.ui.elements.SearchableMultiSelectPreference;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.ui.notifications.AppNotifications;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;
import com.github.timboode.NYP_alert_android.utils.feedback.Volume;
import com.github.timboode.NYP_alert_android.utils.localization.Localization;
import com.github.timboode.NYP_alert_android.utils.os.AndroidSettings;
import com.github.timboode.NYP_alert_android.utils.marketing.GooglePlay;
import com.github.timboode.NYP_alert_android.utils.metadata.AppVersion;
import com.github.timboode.NYP_alert_android.utils.threading.AsyncTaskAdapter;

public class General extends AppCompatPreferenceActivity {
    private static final boolean TODO_IS_REGISTERED = true;
    boolean mIsTesting;

    String mPreviousTopics;

    Preference mRate;
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

    SearchableMultiSelectPreference mTopicSelection;

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
        boolean showTopicSelection = extras.getBoolean(SettingsEvents.SHOW_TOPIC_SELECTION);

        // Show selection?
        if (showTopicSelection) {
            mTopicSelection.showDialog();
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
        mWebsite = findPreference(getString(R.string.websitePref));
        mContact = findPreference(getString(R.string.contactPref));
        mAdvanced = findPreference(getString(R.string.advancedPref));
        mTestAlert = findPreference(getString(R.string.selfTestPref));
        mBatteryOptimization = findPreference(getString(R.string.batteryOptimizationPref));
        mMainCategory = (PreferenceCategory) findPreference(getString(R.string.mainCategoryPref));
        mBatteryOptimizationCategory = (PreferenceCategory)findPreference(getString(R.string.batteryOptimizationCategory));
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
        mTopicSelection.setEntries(-1); // TODO: Populate topics

        // Not registered yet?
        if (TODO_IS_REGISTERED) { // TODO Replace with real registration check
            mNotificationsEnabled.setEnabled(false);
        }

        // Refresh area values
        refreshAreaValues();
    }

    void refreshAreaValues() {
        // Get selected cities
        String selectedCities = Singleton.getSharedPreferences(this).getString(getString(R.string.selectedCitiesPref), getString(R.string.none));

        // Update summary text
        mTopicSelection.setSummary(getString(R.string.selectedTopicsDesc) + "\r\n(" + /*LocationData.getSelectedCityNamesByValues(this, selectedCities, mCitySelection.getEntries(), mCitySelection.getEntryValues()) +*/ ")"); // TODO: Populate topics

        // Save in case the update subscriptions request fails
        if (mPreviousTopics == null) {
            mPreviousTopics = selectedCities;
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
                // TODO: en/disable alert notifications

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

        // Add debug info
        body += getString(R.string.debugInfo) + ": ";

        // Add setting values & push notification device information
        body += "primary.enabled=" + AppPreferences.getNotificationsEnabled(this) + ", ";
        body += "secondary.enabled=" + AppPreferences.getSecondaryNotificationsEnabled(this) + ", ";
        body += "location.enabled=" + AppPreferences.getLocationAlertsEnabled(this) + ", ";

        // Add other params
        body += "volume.primary=" + AppPreferences.getPrimaryAlertVolume(this, -1) + ", ";
        body += "volume.secondary=" + AppPreferences.getSecondaryAlertVolume(this, -1) + ", ";
        body += "sound.primary=" + SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, null, this) + ", ";
        body += "sound.secondary=" + SoundLogic.getAlertSoundName(AlertTypes.SECONDARY, null, this) + ", ";
        body += "popup.enabled=" + AppPreferences.getPopupEnabled(this) + ", ";
        body += "secondaryPopup.enabled=" + AppPreferences.getSecondaryPopupEnabled(this) + ", ";
        body += "wakeScreen.enabled=" + AppPreferences.getWakeScreenEnabled(this) + ", ";
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
        private static final boolean TODO_PUSH_SERVICE_CHECK_FAILED = false;
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
            if (TODO_PUSH_SERVICE_CHECK_FAILED) { // TODO Replace with real push service check
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
                // TODO: Update alert subscriptions
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
                editor.putString(getString(R.string.selectedCitiesPref), mPreviousTopics);

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
                mPreviousTopics = null;
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
}
