package com.github.timboode.NYP_alert_android.ui.elements;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.AttributeSet;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.activities.settings.alerts.SecondaryAlerts;
import com.github.timboode.NYP_alert_android.config.Sound;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.feedback.sound.SoundLogic;
import com.github.timboode.NYP_alert_android.logic.notifications.Notifications;
import com.github.timboode.NYP_alert_android.ui.dialogs.AlertDialogBuilder;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.ui.notifications.AppNotifications;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;

import java.util.Arrays;

public class SoundListPreference extends ListPreference {
    static Context mContext;

    int mSelectedItem;
    CharSequence[] mEntries;
    CharSequence[] mEntryValues;

    public SoundListPreference(Context context, AttributeSet set) {
        super(context, set);

        // Store activity context
        mContext = context;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        // Stop playing sounds
        AppNotifications.clearAll(mContext);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        // Support for RTL languages
        RTLSupport.mirrorDialog(getDialog(), mContext);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        // Get sound names & resource values
        mEntries = getEntries();
        mEntryValues = getEntryValues();

        // Mis-configured?
        if (mEntries == null || mEntryValues == null) {
            throw new IllegalStateException("ListPreference requires an entries array and an entryValues array.");
        }

        // Get currently selected item
        mSelectedItem = getSelectedSoundPosition();

        // Populate dialog with items and set preselected item
        builder.setSingleChoiceItems(mEntries, mSelectedItem,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Get notification manager
                        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(mContext.NOTIFICATION_SERVICE);

                        // Get path to selected sound
                        final String path = mEntryValues[which].toString();

                        // Determine channel ID (primary/secondary)
                        final String alertSoundType = getKey().equals(mContext.getString(R.string.secondarySoundPref)) ? AlertTypes.SECONDARY : AlertTypes.PRIMARY;

                        // Custom sound option selected?
                        if (path.equals(Sound.CUSTOM_SOUND_NAME)) {
                            // Delete (hide) old notification channel
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                notificationManager.deleteNotificationChannel(Notifications.getNotificationChannelId(alertSoundType, "alarm1", mContext));
                            }

                            // Instruct user how to configure custom sound
                            AlertDialogBuilder.showGenericDialog(mContext.getString(R.string.selectCustomSound), mContext.getString(R.string.selectCustomSoundDesc), mContext.getString(R.string.okay), mContext.getString(R.string.notNow), true, getDialog().getContext(), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int which) {
                                    // Clicked okay?
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        // Get channel ID by alert type
                                        String channelId = Notifications.getNotificationChannelId(alertSoundType, Sound.CUSTOM_SOUND_NAME, mContext);

                                        // Ensure notification channel created
                                        Notifications.setNotificationChannel(alertSoundType, path, null, mContext);

                                        // Open notification channel config to allow user to select custom sound
                                        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                                        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
                                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, mContext.getPackageName());

                                        try {
                                            mContext.startActivity(intent);
                                        }
                                        catch (ActivityNotFoundException err) {
                                            // On Android 7 and below, there is no option to set custom sound currently
                                            // As there is no notification channel support
                                            // Show error dialog
                                            AlertDialogBuilder.showGenericDialog(mContext.getString(R.string.error), err.getMessage(), mContext.getString(R.string.okay), null, false, mContext, null);
                                        }

                                        // Save custom sound selection
                                        Singleton.getSharedPreferences(mContext).edit().putString(getKey(), path).commit();

                                        // Dismiss dialog if not null
                                        if (getDialog() != null) {
                                            getDialog().dismiss();
                                        }
                                    }
                                    else {
                                        // Clicked not now, stop playing custom sound
                                        AppNotifications.clearAll(mContext);
                                    }
                                }
                            });
                        }
                        else {
                            // Selected built-in sound
                            // Delete (hide) custom notification channel
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                notificationManager.deleteNotificationChannel(Notifications.getNotificationChannelId(alertSoundType, Sound.CUSTOM_SOUND_NAME, mContext));
                            }
                        }

                        // Update selected item
                        mSelectedItem = which;

                        // Get test type
                        String testAlertType = AlertTypes.TEST;

                        // Secondary notification?
                        if (mContext.getClass().getName().equals(SecondaryAlerts.class.getName())) {
                            // Override type
                            testAlertType = AlertTypes.TEST;
                        }

                        // Stop any previously-selected sound
                        SoundLogic.stopSound(mContext);

                        // Dispatch test notification
                        Notifications.notify(mContext, mContext.getString(R.string.appName), testAlertType, testAlertType, path);
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
        builder.setNegativeButton(android.R.string.cancel, this);
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
