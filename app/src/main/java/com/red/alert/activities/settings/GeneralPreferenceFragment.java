package com.red.alert.activities.settings;

import android.app.AlarmManager;
import android.app.Dialog;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.red.alert.R;
import com.red.alert.activities.Main;
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
import com.red.alert.logic.services.ServiceManager;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.req.SelfTestRequest;
import com.red.alert.ui.elements.MaterialProgressDialog;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.elements.dialogs.SliderPreferenceDialogFragmentCompat;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.marketing.GooglePlay;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.os.AndroidSettings;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import me.pushy.sdk.Pushy;

import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.elements.dialogs.SliderPreferenceDialogFragmentCompat;

public class GeneralPreferenceFragment extends BasePreferenceFragment {
    boolean mIsTesting;
    boolean mFcmTestPassed;
    boolean mPushyTestPassed;

    String mPreviousZones;
    String mPreviousCities;

    private Preference mRate;
    private Preference mDonate;
    private Preference mWebsite;
    private Preference mContact;
    private Preference mAdvanced;
    private Preference mTestAlert;
    private Preference mBatteryOptimization;
    private SwitchPreferenceCompat mNotificationsEnabled;
    private PreferenceCategory mMainCategory;
    private PreferenceCategory mBatteryOptimizationCategory;
    private ListPreference mThemeSelection;
    private ListPreference mLanguageSelection;
    private SearchableMultiSelectPreference mCitySelection;
    private SearchableMultiSelectPreference mZoneSelection;

    BillingClient mBillingClient;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Asked for reload?
        if (key.equalsIgnoreCase(LocationSelectionEvents.LOCATIONS_UPDATED)) {
            // Refresh city/region descriptions
            refreshAreaValues();

            // Update subscriptions on the server-side
            new UpdateSubscriptionsAsync().execute();
        }

        // FCM test passed?
        if (key.equalsIgnoreCase(SelfTestEvents.FCM_TEST_PASSED)) {
            mFcmTestPassed = true;
        }

        // Pushy test passed?
        if (key.equalsIgnoreCase(SelfTestEvents.PUSHY_TEST_PASSED)) {
            mPushyTestPassed = true;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Override transitions for the root settings fragment
        // We want a simple fade when switching tabs, not a slide
        androidx.transition.Fade fadeEnter = new androidx.transition.Fade();
        fadeEnter.setDuration(300);
        setEnterTransition(fadeEnter);

        setReturnTransition(null);
        // setExitTransition(null); // Allow exit transition (Fade) for smooth
        // navigation to sub-screens
        // Do not override reenter transition - let it inherit or use default (Fade)
        // setReenterTransition(null);
    }

    @Override
    protected boolean shouldUpdateTitleInOnResume() {
        // We handle title update manually in onResume via resetToRoot
        return false;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        // Cache resource IDs
        mRate = findPreference(getString(R.string.ratePref));
        mDonate = findPreference(getString(R.string.donatePref));
        mWebsite = findPreference(getString(R.string.websitePref));
        mContact = findPreference(getString(R.string.contactPref));
        mAdvanced = findPreference(getString(R.string.advancedPref));
        mTestAlert = findPreference(getString(R.string.selfTestPref));
        mBatteryOptimization = findPreference(getString(R.string.batteryOptimizationPref));
        mMainCategory = (PreferenceCategory) findPreference(getString(R.string.mainCategoryPref));
        mBatteryOptimizationCategory = (PreferenceCategory) findPreference(
                getString(R.string.batteryOptimizationCategory));
        mCitySelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedCitiesPref)));
        mZoneSelection = ((SearchableMultiSelectPreference) findPreference(getString(R.string.selectedZonesPref)));
        mThemeSelection = (ListPreference) findPreference(getString(R.string.themePref));
        mLanguageSelection = (ListPreference) findPreference(getString(R.string.langPref));
        mNotificationsEnabled = (SwitchPreferenceCompat) findPreference(getString(R.string.enabledPref));

        // Initialize settings
        initializeSettings();

        // Set up listeners
        initializeListeners();

        // Check battery optimization early to prevent flicker
        checkBatteryOptimization();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Broadcasts
        Broadcasts.subscribe(getContext(), this);

        // Reset UI to root state (title, back arrow, etc.)
        if (getActivity() instanceof com.red.alert.activities.Main) {
            ((com.red.alert.activities.Main) getActivity()).resetToRoot(getString(R.string.settings), false);
            
            // Check if we should auto-open cities dialog (from onboarding)
            if (((com.red.alert.activities.Main) getActivity()).mShouldOpenCitiesDialog) {
                // Reset the flag
                ((com.red.alert.activities.Main) getActivity()).mShouldOpenCitiesDialog = false;
                
                // Delay slightly to ensure the fragment is fully ready
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    // Programmatically click on the cities preference to open the dialog
                    if (mCitySelection != null) {
                        mCitySelection.performClick();
                    }
                }, 300);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        Broadcasts.unsubscribe(getContext(), this);
    }

    void checkBatteryOptimization() {
        // Get power manager instance
        PowerManager powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);

        // Android M (6) and up only
        // Check if app is already whitelisted from battery optimizations
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || powerManager.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
            // Remove reminder message about whitelisting the app
            if (mBatteryOptimizationCategory != null) {
                getPreferenceScreen().removePreference(mBatteryOptimizationCategory);
            }
        }
    }

    void initializeSettings() {
        // Set entries & values
        mZoneSelection.setEntries(getResources().getStringArray(R.array.zoneNames));
        mZoneSelection.setEntryValues(getResources().getStringArray(R.array.zoneValues));

        // Set entries & values
        mCitySelection.setEntries(LocationData.getAllCityNames(getContext()));
        mCitySelection.setEntryValues(LocationData.getAllCityValues(getContext()));

        // Not registered yet?
        if (!RedAlertAPI.isRegistered(getContext())) {
            mNotificationsEnabled.setEnabled(false);
        }

        // Refresh area values
        refreshAreaValues();
    }

    void refreshAreaValues() {
        // Get selected cities
        String selectedCities = Singleton.getSharedPreferences(getContext())
                .getString(getString(R.string.selectedCitiesPref), getString(R.string.none));

        // Update summary text
        mCitySelection.setSummary(getString(R.string.selectedCitiesDesc) + "\r\n("
                + LocationData.getSelectedCityNamesByValues(getContext(), selectedCities, mCitySelection.getEntries(),
                        mCitySelection.getEntryValues())
                + ")");

        // Get selected zones
        String selectedZones = Singleton.getSharedPreferences(getContext())
                .getString(getString(R.string.selectedZonesPref), getString(R.string.none));

        // Update summary text
        mZoneSelection.setSummary(getString(R.string.selectedZonesDesc) + "\r\n("
                + LocationData.getSelectedCityNamesByValues(getContext(), selectedZones, mZoneSelection.getEntries(),
                        mZoneSelection.getEntryValues())
                + ")");

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
                    Localization.restoreDefaultLocale(getContext());
                }

                // Notify language changed
                Broadcasts.publish(getContext(), SettingsEvents.THEME_OR_LANGUAGE_CHANGED);

                // Update the preference
                return true;
            }
        });

        // Theme selection
        mThemeSelection.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, final Object value) {
                // Notify theme changed
                Broadcasts.publish(getContext(), SettingsEvents.THEME_OR_LANGUAGE_CHANGED);

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
                AlertDialogBuilder.showGenericDialog(getString(R.string.disableBatteryOptimizations),
                        AndroidSettings.getBatteryOptimizationWhitelistInstructions(getContext()),
                        getString(R.string.okay), getString(R.string.notNow), true, getContext(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                // Clicked okay?
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                    // Request to ignore battery optimizations
                                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                    intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                                    startActivity(intent);
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
                } catch (ActivityNotFoundException ex) {
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
                Pushy.toggleNotifications((boolean) newValue, getContext());

                // Enable/disable location service according to new value
                if (AppPreferences.getLocationAlertsEnabled(getContext())) {
                    // Enabled alerts?
                    if ((boolean) newValue) {
                        // Start the location service
                        ServiceManager.startLocationService(getContext());
                    } else {
                        // Stop the location service
                        ServiceManager.stopLocationService(getContext());
                    }
                }

                // Tell Android to persist new checkbox value
                return true;
            }
        });

        // Rate button
        mRate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Open app page
                GooglePlay.openAppListingPage(getContext());

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
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { Support.CONTACT_EMAIL });
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.appName));
                emailIntent.putExtra(Intent.EXTRA_TEXT, body);

                // Limit to mail apps only
                emailIntent.setSelector(selectorIntent);

                try {
                    // Try to open user's e-mail app
                    startActivity(Intent.createChooser(emailIntent, getString(R.string.contact)));
                } catch (ActivityNotFoundException exc) {
                    // Show a toast instead
                    Toast.makeText(getContext(), R.string.manualContact, Toast.LENGTH_LONG).show();
                }

                // Consume event
                return true;
            }
        });

        // Set up advanced settings listener
        mAdvanced.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Navigate to Advanced Fragment with animation
                if (getActivity() instanceof com.red.alert.activities.Main) {
                    ((com.red.alert.activities.Main) getActivity()).navigateToFragment(new AdvancedPreferenceFragment(),
                            preference.getTitle().toString());
                }
                return true;
            }
        });
    }

    void selfTestCompleted(int result) {
        // Use Material 3 dialog builder
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());

        // Use builder to create dialog
        builder.setTitle(getString(R.string.test)).setMessage(getString(result)).setPositiveButton(R.string.okay,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int id) {
                        // Stop siren (and clear notifications)
                        AppNotifications.clearAll(getContext());

                        // Close dialog
                        dialogInterface.dismiss();
                    }
                });

        // Avoid cancellation
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface Dialog) {
                // Stop siren (and clear notifications)
                AppNotifications.clearAll(getContext());
            }
        });

        try {
            // Build it
            Dialog dialog = builder.create();

            // Show it
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, getContext());
        } catch (Exception exc) {
            // Show toast instead
            Toast.makeText(getContext(), getString(result), Toast.LENGTH_LONG).show();
        }
    }

    String getContactEmailBody() {
        // Add sent via and app version
        String body = getString(R.string.sentVia) + " " + getString(R.string.appName) + " "
                + AppVersion.getVersionName(getContext());

        // Break 2 lines
        body += "\r\n\r\n";

        // Add selected cities
        body += getString(R.string.selectedCities) + ": "
                + LocationData.getSelectedCityNamesByValues(getContext(),
                        Singleton.getSharedPreferences(getContext()).getString(getString(R.string.selectedCitiesPref),
                                ""),
                        LocationData.getAllCityNames(getContext()), LocationData.getAllCityValues(getContext()));

        // Break 1 line
        body += "\r\n";

        // Add selected zones
        body += getString(R.string.selectedZones) + ": "
                + LocationData.getSelectedCityNamesByValues(getContext(),
                        Singleton.getSharedPreferences(getContext()).getString(getString(R.string.selectedZonesPref),
                                ""),
                        getResources().getStringArray(R.array.zoneNames),
                        getResources().getStringArray(R.array.zoneValues));

        // Break 2 lines
        body += "\r\n\r\n";

        // Add selected secondary cities
        body += getString(R.string.selectedSecondaryCities) + ": "
                + LocationData.getSelectedCityNamesByValues(getContext(),
                        Singleton.getSharedPreferences(getContext())
                                .getString(getString(R.string.selectedSecondaryCitiesPref), ""),
                        LocationData.getAllCityNames(getContext()), LocationData.getAllCityValues(getContext()));

        // Break 2 lines
        body += "\r\n\r\n";

        // Add debug info
        body += getString(R.string.debugInfo) + ": ";

        // Add setting values & push notification device information
        body += "user.id=" + RedAlertAPI.getUserId(getContext()) + ", ";
        body += "user.hash=" + RedAlertAPI.getUserHash(getContext()) + ", ";
        body += "primary.enabled=" + AppPreferences.getNotificationsEnabled(getContext()) + ", ";
        body += "secondary.enabled=" + AppPreferences.getSecondaryNotificationsEnabled(getContext()) + ", ";
        body += "location.enabled=" + AppPreferences.getLocationAlertsEnabled(getContext()) + ", ";

        // Check if location alerts enabled
        if (AppPreferences.getLocationAlertsEnabled(getContext())) {
            // Get current location
            Location location = LocationLogic.getCurrentLocation(getContext());

            // Null check
            if (location != null) {
                // Add nearby cities for debugging purposes
                body += "location.nearbyCities=" + LocationData.getNearbyCityNames(location, getContext()) + ", ";
            }

            // Add max distance and update interval
            body += "location.maxDistance=" + LocationLogic.getMaxDistanceKilometers(getContext(), -1) + "km, ";
            body += "location.updateInterval=every "
                    + LocationLogic.getUpdateIntervalMilliseconds(getContext()) / 1000 / 60 + " minute(s), ";
        }

        // Add other params
        body += "volume.primary=" + AppPreferences.getPrimaryAlertVolume(getContext(), -1) + ", ";
        body += "volume.secondary=" + AppPreferences.getSecondaryAlertVolume(getContext(), -1) + ", ";
        body += "sound.primary=" + SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, null, null, getContext()) + ", ";
        body += "sound.secondary=" + SoundLogic.getAlertSoundName(AlertTypes.SECONDARY, null, null, getContext())
                + ", ";
        body += "popup.enabled=" + AppPreferences.getPopupEnabled(getContext()) + ", ";
        body += "secondaryPopup.enabled=" + AppPreferences.getSecondaryPopupEnabled(getContext()) + ", ";
        body += "wakeScreen.enabled=" + AppPreferences.getWakeScreenEnabled(getContext()) + ", ";
        body += "fcm=" + FCMRegistration.isRegistered(getContext()) + ", ";
        body += "fcm.token=" + FCMRegistration.getRegistrationToken(getContext()) + ", ";
        body += "pushy=" + PushyRegistration.isRegistered(getContext()) + ", ";
        body += "pushy.token=" + PushyRegistration.getRegistrationToken(getContext()) + ", ";
        body += "pushy.isConnected=" + Pushy.isConnected() + ", ";
        body += "pushy.foregroundServiceEnabled=" + AppPreferences.getForegroundServiceEnabled(getContext()) + ", ";
        body += "android.sdk=" + Build.VERSION.SDK_INT + ", ";
        body += "android.version=" + Build.VERSION.RELEASE + ", ";

        // Add Wi-Fi connectivity status
        ConnectivityManager connManager = (ConnectivityManager) getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        // Connected?
        if (mWifi != null && mWifi.isConnected()) {
            body += "wifi.isConnected=true, ";
        } else {
            body += "wifi.isConnected=false, ";
        }

        // Add battery optimizations whitelist status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            body += "android.isIgnoringBatteryOptimizations="
                    + ((PowerManager) getContext().getSystemService(Context.POWER_SERVICE))
                            .isIgnoringBatteryOptimizations(getContext().getPackageName())
                    + ", ";
        }

        // Add schedule exact alarms permission status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            body += "android.canScheduleExactAlarms="
                    + ((AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms()
                    + ", ";
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
        if (!PushyRegistration.isRegistered(getContext())) {
            // No need for localization - only shows up in logcat for now
            throw new Exception("The device is not registered for push notifications via Pushy.");
        }

        // Grab registration token
        String token = PushyRegistration.getRegistrationToken(getContext());

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
        if (!FCMRegistration.isRegistered(getContext())) {
            // No need for localization - only shows up in logcat for now
            throw new Exception("The device is not registered for push notifications via FCM.");
        }

        // Grab registration token
        String token = FCMRegistration.getRegistrationToken(getContext());

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
            } catch (Exception exc) {
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
            } catch (Exception exc) {
            }
        }

        // Return the outcome of the test
        return mPushyTestPassed;
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
        mBillingClient = BillingClient.newBuilder(getContext())
                .enablePendingPurchases(params)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(@NonNull BillingResult billingResult,
                            @Nullable List<Purchase> list) {
                        // Successful purchase?
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                            // Traverse all successful purchases
                            for (Purchase purchase : list) {
                                // Item state is purchased?
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    // Consume the purchase (so same item can be repurchased in the future) to
                                    // acknowledge it
                                    consumePurchase(purchase);
                                }
                            }

                            // Show thanks dialog
                            AlertDialogBuilder.showGenericDialog(getString(R.string.donateSuccess),
                                    getString(R.string.donateSuccessDesc), getString(R.string.okay), null, false,
                                    getContext(), null);
                        }
                    }
                }).build();

        // Start the connection after initializing the billing client
        establishConnection();
    }

    private void consumePurchase(Purchase purchase) {
        // Params to consume the purchased consumable item by passing in the purchase
        // token
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
                            .build());
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
                        return Double.compare(p1.getOneTimePurchaseOfferDetails().getPriceAmountMicros(),
                                p2.getOneTimePurchaseOfferDetails().getPriceAmountMicros());
                    }
                });

                // Show dialog with sorted products
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Activity still running?
                            if (getActivity() != null && !getActivity().isFinishing()) {
                                // Show dialog
                                showProductDialog(prodDetailsList);
                            }
                        }
                    });
                }
            }
        });
    }

    private void showProductDialog(List<ProductDetails> productList) {
        // Prepare Material 3 AlertDialog with items list
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());

        // Set dialgo title
        builder.setTitle(R.string.donate);

        // Convert ProductDetails to CharSequence Array (with an additional item for the
        // instructions)
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
            } else if (productList.get(i).getProductId().equals("donation_20")) {
                name = getString(R.string.donation_20);
            } else if (productList.get(i).getProductId().equals("donation_50")) {
                name = getString(R.string.donation_50);
            } else if (productList.get(i).getProductId().equals("donation_100")) {
                name = getString(R.string.donation_100);
            } else if (productList.get(i).getProductId().equals("donation_200")) {
                name = getString(R.string.donation_200);
            }

            // Display local currency amounts in parenthesis
            if (!Objects.requireNonNull(productList.get(i).getOneTimePurchaseOfferDetails()).getPriceCurrencyCode()
                    .equals("ILS")) {
                name += " (" + Objects.requireNonNull(productList.get(i).getOneTimePurchaseOfferDetails())
                        .getFormattedPrice() + ")";
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
        RTLSupport.mirrorDialog(dialog, getContext());
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
        mBillingClient.launchBillingFlow(getActivity(), params);
    }

    public class PerformSelfTestAsync extends AsyncTaskAdapter<Integer, String, Integer> {
        MaterialProgressDialog mLoading;

        public PerformSelfTestAsync() {
            // Prevent concurrent testing
            mIsTesting = true;

            // Fix progress dialog appearance on old devices
            mLoading = new MaterialProgressDialog(getContext());

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
            } catch (Exception exc) {
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
            } catch (Exception exc) {
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
                AlertLogic.processIncomingAlert(AlertTypes.TEST, getString(R.string.testSuccessful), AlertTypes.TEST,
                        null, null, null, getContext());
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
            if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
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
        MaterialProgressDialog mLoading;

        public UpdateSubscriptionsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = new MaterialProgressDialog(getContext());

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
                PushManager.updateSubscriptions(getContext());

                // Update alert subscriptions
                RedAlertAPI.subscribe(getContext());
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

                // Restore previous city/region selections
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(getContext()).edit();

                // Restore original values
                editor.putString(getString(R.string.selectedZonesPref), mPreviousZones);
                editor.putString(getString(R.string.selectedCitiesPref), mPreviousCities);

                // Save and flush to disk
                editor.commit();
            }

            // Activity dead?
            if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
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
                        null, false, getContext(), null);
            } else {
                // Clear previously cached values
                mPreviousZones = null;
                mPreviousCities = null;
            }

            // Refresh city/region setting values
            refreshAreaValues();
        }
    }

    public class UpdateNotificationsAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        MaterialProgressDialog mLoading;

        public UpdateNotificationsAsync() {
            // Fix progress dialog appearance on old devices
            mLoading = new MaterialProgressDialog(getContext());

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
                PushManager.updateSubscriptions(getContext());

                // Update notification preferences
                RedAlertAPI.updateNotificationPreferences(getContext());
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
                SharedPreferences.Editor editor = Singleton.getSharedPreferences(getContext()).edit();

                // Restore original values
                editor.putBoolean(getString(R.string.enabledPref),
                        !AppPreferences.getNotificationsEnabled(getContext()));

                // Save and flush to disk
                editor.commit();
            }

            // Activity dead?
            if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) {
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
                        null, false, getContext(), null);
            }

            // Refresh checkbox with new value
            if (mNotificationsEnabled != null) {
                mNotificationsEnabled.setChecked(AppPreferences.getNotificationsEnabled(getContext()));
            }
        }
    }
}
