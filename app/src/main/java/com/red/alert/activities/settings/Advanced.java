package com.red.alert.activities.settings;

import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.activities.settings.alerts.EarlyWarnings;
import com.red.alert.activities.settings.alerts.LeaveShelterAlerts;
import com.red.alert.activities.settings.alerts.LocationAlerts;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.localization.Localization;

import me.pushy.sdk.Pushy;
import me.pushy.sdk.config.PushyForegroundService;
import me.pushy.sdk.util.PushyServiceManager;

public class Advanced extends AppCompatPreferenceActivity {
    Preference mEarlyWarnings;
    Preference mLeaveShelterAlerts;
    Preference mLocationAlerts;
    Preference mSecondaryAlerts;
    SliderPreference mVolumeSelection;

    CheckBoxPreference mAlertPopup;
    CheckBoxPreference mForegroundService;

    static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1000;

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
    protected void attachBaseContext(Context base) {
        // Reapply locale
        Localization.overridePhoneLocale(base);
        super.attachBaseContext(base);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
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
        addPreferencesFromResource(R.xml.settings_advanced);

        // Cache resource IDs
        mLocationAlerts = findPreference(getString(R.string.locationPref));
        mEarlyWarnings = findPreference(getString(R.string.earlyWarningsPref));
        mLeaveShelterAlerts = findPreference(getString(R.string.leaveShelterAlertsPref));
        mSecondaryAlerts = findPreference(getString(R.string.secondaryPref));
        mVolumeSelection = (SliderPreference) findPreference(getString(R.string.volumePref));
        mAlertPopup = (CheckBoxPreference)findPreference(getString(R.string.alertPopupPref));
        mForegroundService = (CheckBoxPreference)findPreference(getString(R.string.foregroundServicePref));

        // Set up listeners
        initializeListeners();
    }

    void initializeListeners() {
        // Volume selection
        mVolumeSelection.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener() {
            @Override
            public String getDialogMessage(float Value) {
                // Get slider percent
                int percent = (int) (AppPreferences.getPrimaryAlertVolume(Advanced.this, Value) * 100);

                // Generate summary
                return getString(R.string.volumeDesc) + "\r\n(" + percent + "%)";
            }
        });

        // Set up secondary alerts click listener
        mSecondaryAlerts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent secondaryAlerts = new Intent();

                // Set class
                secondaryAlerts.setClass(Advanced.this, SecondaryAlerts.class);

                // Take user to Secondary Alerts settings page
                startActivity(secondaryAlerts);

                // Consume event
                return true;
            }
        });

        // Set up early warnings click listener
        mEarlyWarnings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent earlyWarnings = new Intent();

                // Set class
                earlyWarnings.setClass(Advanced.this, EarlyWarnings.class);

                // Take user to Early Warning settings page
                startActivity(earlyWarnings);

                // Consume event
                return true;
            }
        });

        // Set up leave shelter alerts click listener
        mLeaveShelterAlerts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent leaveShelter = new Intent();

                // Set class
                leaveShelter.setClass(Advanced.this, LeaveShelterAlerts.class);

                // Take user to Leave Shelter Alerts settings page
                startActivity(leaveShelter);

                // Consume event
                return true;
            }
        });

        // Set up location alerts click listener
        mLocationAlerts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent locationAlerts = new Intent();

                // Set class
                locationAlerts.setClass(Advanced.this, LocationAlerts.class);

                // Take user to Location Alerts settings page
                startActivity(locationAlerts);

                // Consume event
                return true;
            }
        });

        // Alert popup checkbox listener
        mAlertPopup.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Android M+: Check if we have permission to draw over other apps
                if ((boolean)newValue == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(Advanced.this)) {
                    // Show permission request dialog
                    AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission), getString(R.string.grantOverlayPermissionInstructions), getString(R.string.okay), getString(R.string.notNow), true, Advanced.this, new DialogInterface.OnClickListener() {
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

        // Foreground service checkbox listener
        mForegroundService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                // Cached Apps Freezer compatibility
                // Force foreground service for Google Pixel devices (Android 15+)
                if (Build.MANUFACTURER.toLowerCase().contains("google") && Build.MODEL.toLowerCase().contains("pixel") && Build.VERSION.SDK_INT >= 35) {
                    // Show foreground service dialog
                    showForegroundServiceDialog();

                    // Toggling off not allowed
                    return false;
                }

                // Stop existing service
                PushyServiceManager.stop(Advanced.this);

                // Toggle foreground service
                Pushy.toggleForegroundService((boolean)value, Advanced.this);

                // Wait 2 seconds
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Start service again
                        PushyServiceManager.start(Advanced.this);
                    }
                }, 2000);

                // Enabled?
                if ((boolean)value == true) {
                    // Show foreground service dialog
                    showForegroundServiceDialog();
                }

                // Tell Android to persist new checkbox value
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

    void showForegroundServiceDialog() {
        // Android O and newer required for notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Show dialog instructing user on how to hide the Pushy foreground service notification
            AlertDialogBuilder.showGenericDialog(getString(R.string.hidePushyForegroundNotification), getString(R.string.hidePushyForegroundNotificationInstructions), getString(R.string.okay), getString(R.string.notNow), true, Advanced.this, new DialogInterface.OnClickListener() {
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
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // User returned from manage overlay permission activity?
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            // Android M+: Check if we have permission to draw over other apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(Advanced.this)) {
                mAlertPopup.setChecked(true);
            }
        }
    }
}
