package com.red.alert.logic.phone;

import android.content.Context;
import android.os.PowerManager;

import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.settings.AppPreferences;

public class PowerManagement {
    public static void wakeUpScreen(String alertType, String city, Context context) {
        // User disabled this feature?
        if (!AppPreferences.getWakeScreenEnabled(context)) {
            // Stop execution
            return;
        }

        // Type must be a primary "alert"
        if (!alertType.equals(AlertTypes.PRIMARY)) {
            // Stop execution
            return;
        }

        // Turn screen on
        wakeUpScreen(context);
    }

    static void wakeUpScreen(Context context) {
        // Get power manager instance
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // Screen off?
        if (!powerManager.isScreenOn()) {
            // Turn on
            PowerManager.WakeLock wl = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "redalert:alert");

            // Release wakelock automatically after 3 seconds
            wl.acquire(3000);
        }
    }
}
