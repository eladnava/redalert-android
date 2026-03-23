package com.red.alert.activities;

import android.app.AlarmManager;
import android.app.NotificationManager;
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
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.WindowCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.installations.FirebaseInstallations;
import com.red.alert.R;
import com.red.alert.config.Sound;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.activities.settings.General;
import com.red.alert.config.Integrations;
import com.red.alert.config.Logging;
import com.red.alert.config.RecentAlerts;
import com.red.alert.config.Safety;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.logic.communication.intents.AlertPopupParameters;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.location.LocationLogic;
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
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.os.AndroidSettings;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import me.pushy.sdk.Pushy;
import me.pushy.sdk.config.PushyForegroundService;
import me.pushy.sdk.lib.jackson.core.JsonFactory;
import me.pushy.sdk.lib.jackson.core.JsonParser;
import me.pushy.sdk.lib.jackson.core.JsonToken;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;
import me.pushy.sdk.util.PushyAuthentication;

public class Main extends AppCompatActivity {
    boolean mIsResumed;
    boolean mIsReloading;
    boolean mIsRegistering;
    boolean mCheckedForUpdates;
    boolean mPushTokensRefreshed;
    boolean mPermissionDialogDisplayed;

    long mNewestAlertId;

    Button mImSafe;
    Runnable mRunnable;
    ListView mAlertsList;
    ProgressBar mLoading;
    MenuItem mLoadingItem;
    LinearLayout mNoAlerts;
    AlertAdapter mAlertsAdapter;
    MenuItem mClearRecentAlertsItem;

    List<Alert> mNewAlerts;
    List<Alert> mDisplayAlerts;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(MainActivityParameters.RELOAD_RECENT_ALERTS)) {
                // Force full reload (disable caching)
                mNewestAlertId = 0;

                // Reload alerts
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

    @Override
    protected void attachBaseContext(Context base) {
        // Reapply locale
        Localization.overridePhoneLocale(base);
        super.attachBaseContext(base);
    }

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

        // Cached Apps Freezer compatibility
        // Force foreground service on Google Pixel devices (Android 15+)
        forceForegroundServiceOnPixelDevices();

        // Delay by 5 seconds
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Clean up old preferences
                AppPreferences.deleteOldSharedPreferences(Main.this);
            }
        }, 5 * 1000);
    }

    void forceForegroundServiceOnPixelDevices() {
        // Only for Google Pixel devices (Android 15+)
        if (!Build.MANUFACTURER.toLowerCase().contains("google") || !Build.MODEL.toLowerCase().contains("pixel") || Build.VERSION.SDK_INT < 35) {
            return;
        }

        // Foreground service not enabled yet?
        if (!AppPreferences.getForegroundServiceEnabled(this)) {
            // Force enable the foreground service
            Singleton.getSharedPreferences(this).edit().putBoolean(getString(R.string.foregroundServicePref), true).commit();
        }
    }

    void showAppUpdateAvailableDialog() {
        // Haven't displayed tutorial?
        if (!AppPreferences.getTutorialDisplayed(this)) {
            return;
        }

        // Not registered?
        if (!RedAlertAPI.isRegistered(Main.this)) {
            return;
        }

        // Haven't registered with FCM/Pushy for notifications?
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this)) {
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Already checked for updates?
        if (mCheckedForUpdates) {
            return;
        }

        // Avoid checking multiple times
        mCheckedForUpdates = true;

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
                alertView.setClass(Main.this, Map.class);

                // Pass alerts directly to map activity
                Map.mAlerts = alert.groupedAlerts;

                // Set expanded alert flag (for determining share message format)
                Map.mIsExpandedAlert = alert.isExpanded;

                // Show it
                startActivity(alertView);
            }
        });

        // No alerts image click listener
        mNoAlerts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // DEBUG ONLY
                //AlertLogic.processIncomingAlert("missiles", "נהריה", "alert", null, null, null, Main.this);
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

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Save state
        mIsResumed = true;

        // Allow other dialogs to be displayed
        mPermissionDialogDisplayed = false;

        // Reload alerts manually
        reloadRecentAlerts();

        // Clear app notifications
        AppNotifications.clearAll(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);

        // Android 13:
        // Ensure notification permission has been granted
        requestNotificationPermission();

        // Ask user to whitelist app from battery optimizations
        showBatteryExemptionDialog();

        // Ask user to enable location permission if necessary
        showLocationPermissionDialog();

        // Android 12+
        // Ask user to allow setting exact alarms if canScheduleExactAlarms() is false (usually only false on Android 13+, but can be disabled manually since Android 12)
        showScheduleExactAlarmsPermissionDialog();

        // Ask user to grant app overlay permission if revoked
        showAlertPopupPermissionDialog();

        // Samsung, Android 11+
        // Ask user to grant notification policy access permission
        // In case custom sound selected
        showNotificationPolicyAccessPermissionDialog();

        // Google Pixel, Android 15+
        // Cached Apps Freezer compatibility
        // Notify user about forced foreground service
        showForcedForegroundServiceDialog();

        // Check for app version updates
        showAppUpdateAvailableDialog();

        // Restart the app services if needed
        ServiceManager.startAppServices(this);

        // Always re-register FCM or Pushy on app start
        if (!mPushTokensRefreshed || !FCMRegistration.isRegistered(Main.this) || !PushyRegistration.isRegistered(Main.this) || !RedAlertAPI.isRegistered(Main.this) || !RedAlertAPI.isSubscribed(Main.this)) {
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
        if (PushyAuthentication.getDeviceCredentials(this) != null && (!Pushy.isPermissionGranted(this) || !NotificationManagerCompat.from(Main.this).areNotificationsEnabled())) {
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

    void showLocationPermissionDialog() {
        // Location alerts disabled?
        if (!AppPreferences.getLocationAlertsEnabled(this)) {
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Can we access the user's location?
        if (!LocationLogic.isLocationAccessGranted(this)) {
            // Request permission via dialog
            LocationLogic.showLocationAccessRequestDialog(this);

            // Prevent other dialogs from being displayed
            mPermissionDialogDisplayed = true;
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
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this)) {
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
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this)) {
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
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this)) {
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

    void showNotificationPolicyAccessPermissionDialog() {
        // Android R (11) and up only
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }

        // Check if device is Samsung and Android R or above
        if (!SoundLogic.isSamsungDeviceRequiringSilentModeOverride()) {
            return;
        }

        // Check if user wants us to override silent mode for either primary or secondary alerts
        if (!SoundLogic.shouldOverrideSilentMode(AlertTypes.PRIMARY, this) && !SoundLogic.shouldOverrideSilentMode(AlertTypes.SECONDARY, this)) {
            return;
        }

        // Check if custom sound configured
        String alertSoundName = SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, ThreatTypes.MISSILES, null, this);
        String earlyWarningSoundName = SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, ThreatTypes.EARLY_WARNING, null, this);
        String leaveShelterSoundName = SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, ThreatTypes.LEAVE_SHELTER, null, this);
        String secondarySoundName = SoundLogic.getAlertSoundName(AlertTypes.SECONDARY, ThreatTypes.MISSILES, null, this);

        // No custom sound selected for any alert sound preference?
        if (!alertSoundName.equals(Sound.CUSTOM_SOUND_NAME) && !earlyWarningSoundName.equals(Sound.CUSTOM_SOUND_NAME) && !leaveShelterSoundName.equals(Sound.CUSTOM_SOUND_NAME) && !secondarySoundName.equals(Sound.CUSTOM_SOUND_NAME)) {
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

        // Get instance of NotificationManager
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Checks if notification policy access permission already granted
        if (notificationManager.isNotificationPolicyAccessGranted()){
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Prevent other dialogs from being displayed
        mPermissionDialogDisplayed = true;

        // Show permission request dialog
        AlertDialogBuilder.showGenericDialog(getString(R.string.notificationPolicyAccessPermission), getString(R.string.notificationPolicyAccessPermissionInstructions), getString(R.string.okay), getString(R.string.notNow), true, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // Clicked okay?
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Bring user to relevant settings activity to grant the notification policy access permission
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                }
            }
        });
    }
    void showForcedForegroundServiceDialog() {
        // Only for Google Pixel devices (Android 15+)
        if (!Build.MANUFACTURER.toLowerCase().contains("google") || !Build.MODEL.toLowerCase().contains("pixel") || Build.VERSION.SDK_INT < 35) {
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

        // Already displayed this exact dialog?
        if (Singleton.getSharedPreferences(this).getBoolean(getString(R.string.forcedForegroundServiceDialogShown), false)) {
            return;
        }

        // Already displayed a permission dialog for this activity?
        if (mPermissionDialogDisplayed) {
            return;
        }

        // Prevent other dialogs from being displayed
        mPermissionDialogDisplayed = true;

        // Show dialog instructing user on how to hide the Pushy foreground service notification
        AlertDialogBuilder.showGenericDialog(getString(R.string.hidePushyForegroundNotification), getString(R.string.hidePushyForegroundNotificationInstructions), getString(R.string.okay), getString(R.string.notNow), true, this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                // Clicked okay?
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Background thread
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // Get notification manager
                            NotificationManager notificationManager = getSystemService(NotificationManager.class);

                            // Wait some time for notification channel to be created
                            while (notificationManager.getNotificationChannel(PushyForegroundService.FOREGROUND_NOTIFICATION_CHANNEL) == null) {
                                try {
                                    Thread.sleep(200);
                                }
                                catch (Exception exc) {
                                    // Ignore exceptions
                                }
                            }

                            // Activity destroyed?
                            if (isDestroyed() || isFinishing()) {
                                return;
                            }

                            // Open notification channel config to allow user to easily disable the notification channel
                            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_CHANNEL_ID, PushyForegroundService.FOREGROUND_NOTIFICATION_CHANNEL);
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(intent);
                        }
                    }).start();
                }
            }
        });

        // Dialog shown
        Singleton.getSharedPreferences(this).edit().putBoolean(getString(R.string.forcedForegroundServiceDialogShown), true).commit();
    }

    void pollRecentAlerts() {
        // Schedule a new timer
        mRunnable = new Runnable() {
            @Override
            public void run() {
                // Activity died?
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                // App is running?
                if (mIsResumed) {
                    // Reload every X seconds
                    reloadRecentAlerts();
                }

                // Schedule for future execution
                mHandler.postDelayed(this, 1000L * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC);
            }
        };

        // Schedule for future invocation
        mHandler.postDelayed(mRunnable, 1000L * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC);
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

    void initializeLiveMapButton(Menu OptionsMenu) {
        // Add live map button
        MenuItem mapItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.alerts));

        // Set map icon
        mapItem.setIcon(R.drawable.ic_map);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mapItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, open Map activity in live mode
        mapItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Start live map activity
                openLiveMap();

                // Consume event
                return true;
            }
        });
    }

    void openLiveMap() {
        // Prepare new intent
        Intent mapIntent = new Intent();

        // Set map activity class
        mapIntent.setClass(Main.this, Map.class);

        // Set live mode
        mapIntent.putExtra(AlertViewParameters.LIVE, true);

        // Ungrouped alerts
        List<Alert> mAllAlerts = new ArrayList<>();

        // Workaround for ConcurrentModificationException
        if (mIsReloading) {
            return;
        }

        // Traverse all alerts
        for (Alert alert: mNewAlerts) {
            // Any grouped alerts?
            if (alert.groupedAlerts.size() > 0) {
                // Add grouped alerts
                for (Alert grouped : alert.groupedAlerts) {
                    mAllAlerts.add(grouped);
                }
            }
            else {
                // Not grouped, add single alert to list
                mAllAlerts.add(alert);
            }
        }

        // Pass all currently-displayed alerts to map activity
        Map.mAlerts = mAllAlerts;

        // Pass newest alert ID
        Map.mNewestAlertId = mNewestAlertId;

        // Start map activity
        startActivity(mapIntent);
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
        settingsIntent.putExtra(SettingsEvents.SHOW_CITY_SELECTION, showZoneSelection);

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

        // Add live map button
        initializeLiveMapButton(OptionsMenu);

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

        // Store JSON as string initially
        String alertsJSON;

        try {
            // Get it from /alerts/all (includes early warnings & leave shelter alerts)
            alertsJSON = HTTP.get("/alerts/all");
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.apiRequestFailed;
        }

        // Prepare list of recent alerts
        List<Alert> recentAlerts = new ArrayList<>();

        try {
            // Create a JsonParser over your JSON string
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(alertsJSON);

            // Make sure it starts with an array
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Expected JSON array");
            }

            // Get Jackson mapper
            ObjectMapper mapper = Singleton.getJackson();

            // Keep track of whether the alert being parsed is the first alert in the list
            boolean firstAlert = true;

            // Iterate over each element in the array
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                // Deserialize a single Alert
                Alert alert = mapper.readValue(parser, Alert.class);

                // Is this the first alert? (most recent)
                if (firstAlert) {
                    // Same ID as the currently displayed first alert?
                    if (alert.id == mNewestAlertId) {
                        // Stop parsing (no new alerts)
                        parser.close();

                        // Loop over currently displayed alerts
                        for (Alert displayedAlert : mDisplayAlerts) {
                            // Update relative time ago
                            displayedAlert.dateString = LocationData.getAlertDateTimeString(displayedAlert.date, displayedAlert.firstGroupedAlertTimestamp, this);
                        }

                        // No new alerts, just refresh relative time ago
                        return R.string.noNewAlerts;
                    }

                    // Save newest alert ID
                    mNewestAlertId = alert.id;
                }

                // No longer the first alert
                firstAlert = false;

                // Add to list
                recentAlerts.add(alert);
            }

            // Close parser
            parser.close();
        } catch (Exception exc) {
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

        // Group alerts within 3-minute range and same threat type
        recentAlerts = groupAlerts(recentAlerts);

        // Localize only grouped alerts
        localizeGroupedAlerts(recentAlerts);

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

        // Alert grouping date cutoff threshold (seconds)
        int dateGroupingThreshold = 3 * 60;

        // Traverse elements
        for (int i = 0; i < alerts.size(); i++) {
            // Current element
            Alert current = alerts.get(i);

            // Check if can group the current and the last alert
            if (lastAlert != null &&
                    current.date >= lastAlert.date - dateGroupingThreshold && current.date <= lastAlert.date + dateGroupingThreshold &&
                    current.threat.equals(lastAlert.threat)) {
                // Lazily allocate lists
                if (lastAlert.groupedAlerts == null) {
                    lastAlert.groupedAlerts = new ArrayList<>(4);
                    lastAlert.groupedCities = new ArrayList<>(4);
                }

                // Add current alert object to grouped alerts list
                lastAlert.groupedAlerts.add(current);
                lastAlert.groupedCities.add(current.city);

                // Save first grouped alert timestamp for later use (alert refresh)
                if (current.date != lastAlert.date) {
                    lastAlert.firstGroupedAlertTimestamp = current.date;
                }
            } else {
                // New alert (not grouped with previous item)
                current.groupedAlerts = new ArrayList<>(4);
                current.groupedCities = new ArrayList<>(4);

                // Add itself as the only "grouped" alert
                current.groupedAlerts.add(current);
                current.groupedCities.add(current.city);

                // Add to final result list
                groupedAlerts.add(current);

                // Set last as current
                lastAlert = current;
            }
        }

        // Return grouped alerts
        return groupedAlerts;
    }

    private void localizeGroupedAlerts(List<Alert> alerts) {
        // Cache locale
        boolean isArabic = Localization.isArabic(this);

        // Traverse alerts
        for (Alert alert : alerts) {
            // Get relative date string for this alert
            alert.dateString = LocationData.getAlertDateTimeString(alert.date, alert.firstGroupedAlertTimestamp, this);

            // Localize threat once
            alert.localizedThreat = StringUtils.capitalizeAllWords(LocationData.getLocalizedThreatType(alert.threat, this));

            // Prepare unique hash maps of cities and descriptions
            LinkedHashMap<String, String> uniqueCities = new LinkedHashMap<>();
            LinkedHashMap<String, String> uniqueDescriptions = new LinkedHashMap<>();

            // Traverse grouped cities
            for (String city : alert.groupedCities) {
                // Localize city name
                String localizedCity = LocationData.getLocalizedCityName(city, this);

                // Localize zone
                String desc = LocationData.getLocalizedZoneByCityName(city, this);

                // Check whether city should be highlighted in bold
                if (AlertLogic.isCitySelectedPrimarily(city, true, this)
                        || AlertLogic.isNearby(city, this)
                        || AlertLogic.isSecondaryCitySelected(city, true, this)) {
                    localizedCity = "<b>" + localizedCity + "</b>";
                }

                // Ensure each city only appears once
                uniqueCities.put(city, localizedCity);

                // Description exists?
                if (!StringUtils.stringIsNullOrEmpty(desc)) {
                    // Get zone name by city
                    String zoneName = LocationData.getLocalizedZoneByCityName(city, this);

                    // Only add description if this zone name hasn't been added yet
                    if (!uniqueDescriptions.containsKey(zoneName)) {
                        uniqueDescriptions.put(zoneName, desc);
                    }
                }
            }

            // Convert hash maps to lists
            List<String> localizedCities = new ArrayList<>(uniqueCities.values());
            List<String> descriptionsList = new ArrayList<>(uniqueDescriptions.values());

            // Sort city & zone names alphabetically
            Collections.sort(localizedCities);
            Collections.sort(descriptionsList);

            // Join lists into CSV strings
            alert.localizedCity = TextUtils.join(", ", localizedCities);
            alert.desc = TextUtils.join(", ", descriptionsList);

            // Get grouped city count
            int groupedCityCount = localizedCities.size();

            // Display {threat} • {count} Cities instead of entire list of cities
            alert.localizedTitle = alert.localizedThreat;

            // If less than 15 cities, display all city names in large font
            if (groupedCityCount >= 15) {
                // Display {threat} • {count} Cities instead of entire list of cities
                alert.localizedTitle = alert.localizedThreat + " • " + groupedCityCount + " " + getString(R.string.selectedCities);

                // Convert Arabic numerals to digits if needed
                if (isArabic) {
                    alert.localizedTitle = Localization.localizeDigits(alert.localizedTitle, this);
                }
            }
        }

        // Preserve expanded state based on date
        for (Alert displayAlert : mDisplayAlerts) {
            // Expanded?
            if (displayAlert.isExpanded) {
                // Check for this alert in the new list
                for (Alert newAlert : alerts) {
                    // Same date?
                    if (newAlert.date == displayAlert.date) {
                        // Set as expanded
                        newAlert.isExpanded = true;

                        // Stop after finding the first match
                        break;
                    }
                }
            }
        }
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
            updateInfo = Singleton.getJackson().readValue(updateJson, VersionInfo.class);
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
        // Countdown timer running?
        if (mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
        }

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);

        // Destroy activity
        super.onDestroy();
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

        // Ask user to select their city
        String desc = getString(R.string.pushRegistrationSuccessDesc);

        // Check if upgrading from previous version
        if (Singleton.getSharedPreferences(this).getBoolean("tutorial_1_0_23", false)) {
            // Ask user to reselect
            desc = getString(R.string.pushRegistrationReselectDesc);
        }

        // Build the dialog
        AlertDialogBuilder.showGenericDialog(getString(R.string.pushRegistrationSuccess), desc, getString(R.string.okay), null, false, this, new DialogInterface.OnClickListener() {
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
            // No new alerts?
            else if (errorStringResource == R.string.noNewAlerts) {
                // Invalidate the list (for relative time ago to be refreshed)
                mAlertsAdapter.notifyDataSetChanged();
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

            // Retry mechanism (for temporary network errors)
            int tries = 0;

            // Retry up to 5 times
            while (tries <= 5) {
                try {
                    // Increment tries
                    tries++;

                    // Register for Pushy push notifications
                    String pushyToken = PushyRegistration.registerForPushNotifications(Main.this);

                    // Register for FCM push notifications
                    String fcmToken = FCMRegistration.registerForPushNotifications(Main.this);

                    // First time registering with the API?
                    if (!RedAlertAPI.isRegistered(Main.this)) {
                        // Register with RedAlert API and store user ID & hash
                        RedAlertAPI.register(fcmToken, pushyToken, Main.this);
                    } else {
                        // FCM or Pushy token have changed?
                        if ((previousFirebaseToken != null && !previousFirebaseToken.equals(fcmToken)) ||
                                (previousPushyToken != null && !previousPushyToken.equals(pushyToken))) {
                            // Update token server-side
                            RedAlertAPI.updatePushTokens(fcmToken, pushyToken, Main.this);
                        }
                    }

                    // First time subscribing with the API?
                    if (!RedAlertAPI.isSubscribed(Main.this)) {
                        // Update Pub/Sub subscriptions
                        PushManager.updateSubscriptions(Main.this);

                        // Update notification preferences
                        RedAlertAPI.updateNotificationPreferences(Main.this);

                        // Subscribe for alerts based on current city/region selections
                        RedAlertAPI.subscribe(Main.this);
                    }

                    // If we're here, success
                    break;
                } catch (Exception exc) {
                    // Workaround for FCM error "Invalid argument for the given fid"
                    if (exc.getMessage() != null && exc.getMessage().contains("Invalid argument for the given fid")) {
                        try {
                            // Try to delete Firebase Installations ID
                            Tasks.await(FirebaseInstallations.getInstance().delete());
                        } catch (Exception e) {
                            // Log failure
                            Log.e(Logging.TAG, "Firebase installation deletion failed", e);
                        }
                    }

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

    public class CheckForUpdatesAsync extends AsyncTaskAdapter<Integer, String, String> {
        @Override
        protected String doInBackground(Integer... Parameter) {
            // Try to check for updates
            return checkForUpdates();
        }

        @Override
        protected void onPostExecute(String newVersion) {
            // Activity dead?
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // Got a return code?
            if (!StringUtils.stringIsNullOrEmpty(newVersion)) {
                // Show update dialog
                UpdateDialogs.showUpdateDialog(Main.this, newVersion);
            }
        }
    }

    void handleNotificationClick(Intent intent) {
        // Extract clicked notification params
        String[] cities = intent.getStringArrayExtra(AlertPopupParameters.CITIES);
        String threatType = intent.getStringExtra(AlertPopupParameters.THREAT_TYPE);
        String instructions = intent.getStringExtra(AlertPopupParameters.INSTRUCTIONS);
        long timestamp = intent.getLongExtra(AlertPopupParameters.TIMESTAMP, 0);

        // Display popup if all necessary parameters are set
        if (cities != null && cities.length > 0 && threatType != null && timestamp > 0) {
            // Fetch highest priority countdown in seconds for given alert cities list
            int countdown = LocationData.getPrioritizedCountdownForCities(cities, this);

            // Check for old (inactive) alert
            // Only display alert popup for currently active alerts (+ 10 minutes)
            if (!threatType.equals(ThreatTypes.EARLY_WARNING) && !threatType.equals(ThreatTypes.LEAVE_SHELTER) && timestamp < (DateTime.getUnixTimestamp() - countdown - (Safety.POST_IMPACT_WAIT_MINUTES * 60)) ) {
                return;
            }

            // Create new popup intent
            final Intent popupIntent = new Intent();

            // Set class to popup activity
            popupIntent.setClass(this, Popup.class);

            // Pass on city name, threat type, and alert received timestamp
            popupIntent.putExtra(AlertPopupParameters.CITIES, cities);
            popupIntent.putExtra(AlertPopupParameters.TIMESTAMP, timestamp);
            popupIntent.putExtra(AlertPopupParameters.THREAT_TYPE, threatType);
            popupIntent.putExtra(AlertPopupParameters.INSTRUCTIONS, instructions);

            // Clear top, set as new task
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            // Delay popup by 300ms to allow main activity UI to finish rendering
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Display popup activity
                    startActivity(popupIntent);
                }
            }, 300);

            // Get intent extras
            Bundle extras = intent.getExtras();

            // Clear intent extras to avoid showing same popup again on re-entering activity
            if (extras != null) {
                extras.clear();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Just granted location permission?
        if (requestCode == LocationLogic.LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            // Location alerts enabled?
            if (AppPreferences.getLocationAlertsEnabled(this)) {
                // Start the location service
                ServiceManager.startLocationService(this);
            }
        }
    }
}
