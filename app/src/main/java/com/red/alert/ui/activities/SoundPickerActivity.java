package com.red.alert.ui.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.config.Sound;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.notifications.RocketNotifications;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;

public class SoundPickerActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();
    }

    private int getSelectedSoundPosition() {
        // Get selected sound
        Uri selectedSound = getIntent().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);

        // Got one?
        if (selectedSound != null) {
            // Get path to sound
            String soundFilePath = selectedSound.toString();

            // Remove prefix
            soundFilePath = soundFilePath.replace(Sound.APP_SOUND_PREFIX, "");

            // Get sound values
            String[] soundValues = getSoundValues();

            // Get selected sound
            for (int position = 0; position < soundValues.length; position++) {
                // Do we have a match?
                if (soundValues[position].equals(soundFilePath)) {
                    // Return item position
                    return position;
                }
            }
        }

        // No item selected
        return -1;
    }

    private void initializeUI() {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // No cancellation
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface Dialog) {
                // Stop playing sounds
                onDialogCancelled(Dialog);
            }
        });

        // Insert version into message
        builder.setSingleChoiceItems(getSounds(), getSelectedSoundPosition(),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface Dialog, int Item) {
                        // No calling activity?
                        if (getCallingActivity() == null) {
                            return;
                        }

                        // Get path to selected sound
                        String path = getSoundValues()[Item].toString();

                        // Get test type
                        String testAlertType = AlertTypes.TEST_SOUND;

                        // Secondary notification?
                        if (getCallingActivity().getClassName().equals(SecondaryAlerts.class.getName())) {
                            // Override type
                            testAlertType = AlertTypes.TEST_SECONDARY_SOUND;
                        }

                        // Dispatch test notification
                        RocketNotifications.notify(SoundPickerActivity.this, null, getString(R.string.appName), getString(R.string.testSound), testAlertType, path);
                    }
                });

        // Use builder to create dialog
        builder.setTitle(getString(R.string.sounds));

        // Set positive button
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface DialogInterface, int i) {
                // Convert to AlertDialog
                AlertDialog dialog = (AlertDialog) DialogInterface;

                // Convert to AlertDialog
                int checkedItem = dialog.getListView().getCheckedItemPosition();

                // No selection?
                if (checkedItem == -1) {
                    // Close dialog and set result as failed
                    onDialogCancelled(dialog);

                    // Stop execution
                    return;
                }

                // Get path to selected sound
                String selectedResource = getSoundValues()[checkedItem].toString();

                // Prepare result intent
                Intent resultIntent = new Intent();

                // Convert to URI
                Uri alarmSoundURI = Uri.parse(Sound.APP_SOUND_PREFIX + selectedResource);

                // Insert URI into extras
                resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, alarmSoundURI);

                // Set activity result
                setResult(RESULT_OK, resultIntent);

                // Close dialog
                dialog.dismiss();

                // Stop playing sounds
                AppNotifications.clearAll(SoundPickerActivity.this);

                // Close activity
                finish();
            }
        });

        // Set negative button
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface Dialog, int i) {
                // Stop playing sounds
                onDialogCancelled(Dialog);
            }
        });

        // Create the dialog
        AlertDialog dialog = builder.create();

        try {
            // Show dialog
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, this);
        }
        catch (Exception exc) {
            // Show toast instead
            Toast.makeText(SoundPickerActivity.this, exc.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void onDialogCancelled(DialogInterface dialog) {
        // Close dialog
        dialog.dismiss();

        // Stop playing sounds
        AppNotifications.clearAll(SoundPickerActivity.this);

        // Set bad result
        setResult(RESULT_CANCELED);

        // Close activity
        finish();
    }

    private String[] getSounds() {
        // Get sound titles
        return getResources().getStringArray(R.array.sounds);
    }

    private String[] getSoundValues() {
        // Get sound values
        return getResources().getStringArray(R.array.soundValues);
    }
}
