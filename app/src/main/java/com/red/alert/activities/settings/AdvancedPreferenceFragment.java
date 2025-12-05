package com.red.alert.activities.settings;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.red.alert.R;
import com.red.alert.ui.dialogs.AlertDialogBuilder;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import me.pushy.sdk.Pushy;
import me.pushy.sdk.config.PushyForegroundService;
import me.pushy.sdk.util.PushyServiceManager;

public class AdvancedPreferenceFragment extends BasePreferenceFragment {
    TwoStatePreference mAlertPopup;
    TwoStatePreference mForegroundService;
    
    // Activity result launcher for overlay permission
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register for activity result (overlay permission)
        overlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Check if we have permission now
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(getActivity())) {
                    if (mAlertPopup != null) {
                        mAlertPopup.setChecked(true);
                    }
                }
            }
        );
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_advanced, rootKey);

        // Cache preferences
        mAlertPopup = findPreference(getString(R.string.alertPopupPref));
        mForegroundService = findPreference(getString(R.string.foregroundServicePref));

        // Secondary Alerts
        findPreference(getString(R.string.secondaryPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity()).navigateToFragment(
                                    new SecondaryAlertsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Early Warnings
        findPreference(getString(R.string.earlyWarningsPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity())
                                    .navigateToFragment(new EarlyWarningsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Leave Shelter Alerts
        findPreference(getString(R.string.leaveShelterAlertsPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity()).navigateToFragment(
                                    new LeaveShelterAlertsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Location Alerts
        findPreference(getString(R.string.locationPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity())
                                    .navigateToFragment(new LocationAlertsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Alert Popup
        if (mAlertPopup != null) {
            mAlertPopup.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // Enabled?
                    if ((boolean) newValue) {
                        // Check for overlay permission
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                && !Settings.canDrawOverlays(getActivity())) {
                            // Show permission dialog
                            AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission),
                                    getString(R.string.grantOverlayPermissionInstructions),
                                    getString(R.string.okay),
                                    getString(R.string.notNow), true, getActivity(), (dialogInterface, which) -> {
                                        if (which == DialogInterface.BUTTON_POSITIVE) {
                                            // Use activity result launcher instead of startActivityForResult
                                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:" + getActivity().getPackageName()));
                                            overlayPermissionLauncher.launch(intent);
                                        }
                                    });

                            // Don't enable yet
                            return false;
                        }
                    }
                    return true;
                }
            });
        }

        // Foreground Service
        if (mForegroundService != null) {
            mForegroundService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    // Cached Apps Freezer compatibility
                    // Force foreground service for Google Pixel devices (Android 15+)
                    if (Build.MANUFACTURER.toLowerCase().contains("google") 
                            && Build.MODEL.toLowerCase().contains("pixel") 
                            && Build.VERSION.SDK_INT >= 35) {
                        // Show foreground service dialog
                        showForegroundServiceDialog();
                        // Toggling off not allowed
                        return false;
                    }

                    // Stop existing service
                    PushyServiceManager.stop(getActivity());

                    // Toggle foreground service
                    Pushy.toggleForegroundService((boolean) value, getActivity());

                    // Wait 2 seconds then restart service
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (getActivity() != null) {
                                PushyServiceManager.start(getActivity());
                            }
                        }
                    }, 2000);

                    // Enabled?
                    if ((boolean) value) {
                        // Show foreground service dialog
                        showForegroundServiceDialog();
                    }

                    // Tell Android to persist new checkbox value
                    return true;
                }
            });
        }
    }

    void showForegroundServiceDialog() {
        // Android O and newer required for notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getActivity() != null) {
            // Show dialog instructing user on how to hide the Pushy foreground service notification
            AlertDialogBuilder.showGenericDialog(
                    getString(R.string.hidePushyForegroundNotification),
                    getString(R.string.hidePushyForegroundNotificationInstructions),
                    getString(R.string.okay),
                    getString(R.string.notNow),
                    true,
                    getActivity(),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            // Clicked okay?
                            if (which == DialogInterface.BUTTON_POSITIVE && getActivity() != null) {
                                // Background thread
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (getActivity() == null) return;
                                        
                                        // Get notification manager
                                        NotificationManager notificationManager = 
                                                (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);

                                        // Wait some time for notification channel to be created
                                        while (notificationManager.getNotificationChannel(
                                                PushyForegroundService.FOREGROUND_NOTIFICATION_CHANNEL) == null) {
                                            try {
                                                Thread.sleep(200);
                                            } catch (Exception exc) {
                                                // Ignore exceptions
                                            }
                                        }

                                        // Activity destroyed?
                                        if (getActivity() == null || getActivity().isDestroyed() || getActivity().isFinishing()) {
                                            return;
                                        }

                                        // Open notification channel config to allow user to easily disable the notification channel
                                        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                                        intent.putExtra(Settings.EXTRA_CHANNEL_ID, PushyForegroundService.FOREGROUND_NOTIFICATION_CHANNEL);
                                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getActivity().getPackageName());
                                        startActivity(intent);
                                    }
                                }).start();
                            }
                        }
                    });
        }
    }
}