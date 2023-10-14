package com.red.alert.logic.feedback.sound;

import android.content.Context;
import android.media.AudioManager;

import com.red.alert.config.Sound;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.settings.AppPreferences;

public class VolumeLogic {
    public static void setStreamVolume(String alertType, Context context) {
        // Get the audio manager
        AudioManager audioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);

        // Get max possible volume
        int requestedVolume = VolumeLogic.getNotificationVolume(alertType, context);

        try {
            // Set volume to desired level
            audioManager.setStreamVolume(Sound.STREAM_TYPE, requestedVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }
        catch (SecurityException exc) {
            // Ignore SecurityException: Not allowed to change Do Not Disturb state
        }
    }

    public static int getNotificationVolume(String alertType, Context context) {
        // Get default volume multiplier
        float volumePercent = AppPreferences.getPrimaryAlertVolume(context, -1);

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND)) {
            volumePercent = AppPreferences.getSecondaryAlertVolume(context, -1);
        }

        // Multiply by max volume
        return (int) (getMaxNotificationVolume(context) * volumePercent);
    }

    public static int getMaxNotificationVolume(Context context) {
        // Get the audio manager
        AudioManager audioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);

        // Get max possible volume
        return audioManager.getStreamMaxVolume(Sound.STREAM_TYPE);
    }
}
