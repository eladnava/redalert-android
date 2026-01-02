package com.red.alert.ui.elements;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.red.alert.R;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.config.Sound;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.logic.notifications.Notifications;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.caching.Singleton;

import java.util.Arrays;

public class SoundListPreference extends ListPreference {
    static Context mContext;
    private AlertDialog mDialog;

    int mSelectedItem;
    CharSequence[] mEntries;
    CharSequence[] mEntryValues;

    public SoundListPreference(Context context, AttributeSet set) {
        super(context, set);

        // Store activity context
        mContext = context;
    }

    @Override
    protected void onClick() {
        // Show custom dialog instead of default
        showSoundDialog();
    }

    private void showSoundDialog() {
        // Get sound names & resource values
        mEntries = getEntries();
        mEntryValues = getEntryValues();

        // Mis-configured?
        if (mEntries == null || mEntryValues == null) {
            throw new IllegalStateException("ListPreference requires an entries array and an entryValues array.");
        }

        // Get currently selected item
        mSelectedItem = getSelectedSoundPosition();

        // Use Material 3 dialog builder
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext);

        builder.setTitle(getTitle());

        // Populate dialog with items and set preselected item
        builder.setSingleChoiceItems(mEntries, mSelectedItem,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Get notification manager
                        final NotificationManager notificationManager = (NotificationManager) mContext
                                .getSystemService(mContext.NOTIFICATION_SERVICE);

                        // Get path to selected sound
                        final String path = mEntryValues[which].toString();

                        // Determine channel ID (primary/secondary)
                        final String alertSoundType = getKey().equals(mContext.getString(R.string.secondarySoundPref))
                                ? AlertTypes.SECONDARY
                                : AlertTypes.PRIMARY;

                        // Determine threat type (primary/seconcdary)
                        String soundThreatType = (mContext.getClass().getName().equals(SecondaryAlerts.class.getName()))
                                ? AlertTypes.TEST_SECONDARY_SOUND
                                : AlertTypes.TEST_SOUND;

                        // Early Warning sound selection?
                        if (getKey().equals(mContext.getString(R.string.earlyWarningsSoundPref))) {
                            soundThreatType = ThreatTypes.EARLY_WARNING;
                        }

                        // Leave Shelter sound selection?
                        if (getKey().equals(mContext.getString(R.string.leaveShelterAlertsSoundPref))) {
                            soundThreatType = ThreatTypes.LEAVE_SHELTER;
                        }

                        // Convert to final
                        final String alertThreatType = soundThreatType;

                        // Custom sound option selected?
                        if (path.equals(Sound.CUSTOM_SOUND_NAME)) {
                            // Delete (hide) old notification channel
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                notificationManager.deleteNotificationChannel(Notifications
                                        .getNotificationChannelId(alertSoundType, alertThreatType, "alarm1", mContext));
                            }

                            // Instruct user how to configure custom sound
                            AlertDialogBuilder.showGenericDialog(mContext.getString(R.string.selectCustomSound),
                                    mContext.getString(R.string.selectCustomSoundDesc),
                                    mContext.getString(R.string.okay), mContext.getString(R.string.notNow), true,
                                    mContext, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int which) {
                                            // Clicked okay?
                                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                                // Get channel ID by alert type
                                                String channelId = Notifications.getNotificationChannelId(
                                                        alertSoundType, alertThreatType, Sound.CUSTOM_SOUND_NAME,
                                                        mContext);

                                                // Ensure notification channel created
                                                Notifications.setNotificationChannel(alertSoundType, alertThreatType,
                                                        path, null, mContext);

                                                // Open notification channel config to allow user to select custom sound
                                                Intent intent = new Intent(
                                                        Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                                                intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
                                                intent.putExtra(Settings.EXTRA_APP_PACKAGE, mContext.getPackageName());

                                                try {
                                                    mContext.startActivity(intent);
                                                } catch (ActivityNotFoundException err) {
                                                    // On Android 7 and below, there is no option to set custom sound
                                                    // currently
                                                    // As there is no notification channel support
                                                    // Show error dialog
                                                    AlertDialogBuilder.showGenericDialog(
                                                            mContext.getString(R.string.error), err.getMessage(),
                                                            mContext.getString(R.string.okay), null, false, mContext,
                                                            null);
                                                }

                                                // Save custom sound selection
                                                Singleton.getSharedPreferences(mContext).edit()
                                                        .putString(getKey(), path).commit();

                                                // Dismiss dialog if not null
                                                if (mDialog != null) {
                                                    mDialog.dismiss();
                                                }
                                            } else {
                                                // Clicked not now, stop playing custom sound
                                                AppNotifications.clearAll(mContext);
                                            }
                                        }
                                    });
                        } else {
                            // Selected built-in sound
                            // Delete (hide) custom notification channel
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                notificationManager.deleteNotificationChannel(Notifications.getNotificationChannelId(
                                        alertSoundType, alertThreatType, Sound.CUSTOM_SOUND_NAME, mContext));
                            }
                        }

                        // Update selected item
                        mSelectedItem = which;

                        // Get test type
                        String testAlertType = AlertTypes.TEST_SOUND;

                        // Secondary notification?
                        if (mContext.getClass().getName().equals(SecondaryAlerts.class.getName())) {
                            // Override type
                            testAlertType = AlertTypes.TEST_SECONDARY_SOUND;
                        }

                        // Stop any previously-selected sound
                        SoundLogic.stopSound(mContext);

                        // Dispatch test notification
                        Notifications.notify(mContext,
                                Arrays.asList(new String[] { mContext.getString(R.string.appName) }), testAlertType,
                                alertThreatType, path, null);
                    }
                });

        // OK button
        builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Get path to selected sound
                String selectedSoundResource = mEntryValues[mSelectedItem].toString();

                // Save selected sound
                Singleton.getSharedPreferences(mContext).edit().putString(getKey(), selectedSoundResource).commit();
            }
        });

        // Cancel button
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Stop playing sounds
                AppNotifications.clearAll(mContext);
            }
        });

        // Set dismiss listener
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // Stop playing sounds
                AppNotifications.clearAll(mContext);
            }
        });

        // Create dialog
        mDialog = builder.create();

        // Show it
        mDialog.show();

        // Support for RTL languages
        RTLSupport.mirrorDialog(mDialog, mContext);
    }

    private int getSelectedSoundPosition() {
        // Get selected sound
        String soundFilePath = Singleton.getSharedPreferences(mContext).getString(getKey(), null);

        // Got one?
        if (soundFilePath != null) {
            // Remove prefix
            soundFilePath = soundFilePath.replace(Sound.APP_SOUND_PREFIX, "");

            // Get selected sound
            for (int position = 0; position < mEntryValues.length; position++) {
                // Do we have a match?
                if (mEntryValues[position].equals(soundFilePath)) {
                    // Return item position
                    return position;
                }
            }
        }

        // No item selected
        return -1;
    }
}
