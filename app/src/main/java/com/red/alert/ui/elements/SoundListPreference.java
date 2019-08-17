package com.red.alert.ui.elements;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.red.alert.R;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.config.Sound;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.notifications.RocketNotifications;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.caching.Singleton;

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
                        // Update selected item
                        mSelectedItem = which;

                        // Get path to selected sound
                        String path = mEntryValues[which].toString();

                        // Get test type
                        String testAlertType = AlertTypes.TEST_SOUND;

                        // Secondary notification?
                        if (mContext.getClass().getName().equals(SecondaryAlerts.class.getName())) {
                            // Override type
                            testAlertType = AlertTypes.TEST_SECONDARY_SOUND;
                        }

                        // Dispatch test notification
                        RocketNotifications.notify(mContext, null, mContext.getString(R.string.appName), mContext.getString(R.string.testSound), testAlertType, path);

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
