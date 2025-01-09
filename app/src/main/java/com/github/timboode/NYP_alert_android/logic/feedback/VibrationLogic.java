package com.github.timboode.NYP_alert_android.logic.feedback;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.config.Vibration;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.feedback.sound.SoundLogic;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;

public class VibrationLogic {
    public static boolean shouldVibrate(String alertType, Context context) {
        // Testing sounds (or system alert)?
        if (alertType.equals(AlertTypes.TEST_SOUND) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND) || alertType.equals(AlertTypes.SYSTEM)) {
            return false;
        }

        // Get the audio manager
        AudioManager audioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);

        // Phone is on silent?
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            // Can't override silent mode?
            if (!SoundLogic.shouldOverrideSilentMode(alertType, context)) {
                return false;
            }
        }

        // All good
        return true;
    }

    public static boolean isVibrationEnabled(String alertType, Context context) {
        // Get enabled / disabled setting
        boolean enabled = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.vibratePref), true);

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY)) {
            enabled = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.secondaryVibratePref), true);
        }

        // Return value
        return enabled;
    }

    public static void stopVibration(Context context) {
        // Get vibration service
        Vibrator vibratorService = (Vibrator) context.getSystemService(context.VIBRATOR_SERVICE);

        // Cancel any current vibrations
        vibratorService.cancel();
    }

    public static void issueVibration(String alertType, Context context) {
        // Enable vibration?
        if (!isVibrationEnabled(alertType, context)) {
            return;
        }

        // Should we vibrate?
        if (!shouldVibrate(alertType, context)) {
            return;
        }

        // Get vibration service
        Vibrator vibrator = (Vibrator) context.getSystemService(context.VIBRATOR_SERVICE);

        // Check if vibration is supported by the device
        if (vibrator.hasVibrator()) {
            // Special code for Android O and up to vibrate with the app in the background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Set alarm  audio attributes
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();

                // Create vibration effect
                VibrationEffect effect = VibrationEffect.createWaveform(Vibration.VIBRATION_PATTERN, -1);

                // Vibrate the device
                vibrator.vibrate(effect, audioAttributes);
            } else {
                // Before Android O, things were easier
                vibrator.vibrate(Vibration.VIBRATION_PATTERN, -1);
            }
        }

    }
}
