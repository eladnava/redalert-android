package com.github.timboode.NYP_alert_android.logic.feedback.sound;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.config.Sound;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;

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
            // Log SecurityException: Not allowed to change Do Not Disturb state
            Log.d(Logging.TAG, "Error changing alarm stream volume", exc);
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
