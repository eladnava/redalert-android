package com.red.alert.utils.feedback;

import android.content.Context;
import android.os.Vibrator;

import com.red.alert.logic.feedback.VibrationLogic;

public class Vibration {
    public static void stopVibration(Context context) {
        // Get vibration service
        Vibrator vibratorService = (Vibrator) context.getSystemService(context.VIBRATOR_SERVICE);

        // Cancel any current vibrations
        vibratorService.cancel();
    }

    public static void issueVibration(String alertType, Context context) {
        // Enable vibration?
        if (!VibrationLogic.isVibrationEnabled(alertType, context)) {
            return;
        }

        // Should we vibrate?
        if (!VibrationLogic.shouldVibrate(alertType, context)) {
            return;
        }

        // Get vibration service
        Vibrator vibratorService = (Vibrator) context.getSystemService(context.VIBRATOR_SERVICE);

        // Play only once
        vibratorService.vibrate(com.red.alert.config.Vibration.VIBRATION_PATTERN, -1);
    }

}
