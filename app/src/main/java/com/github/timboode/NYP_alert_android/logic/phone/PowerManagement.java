package com.github.timboode.NYP_alert_android.logic.phone;

import android.content.Context;
import android.os.PowerManager;

import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;

public class PowerManagement {
    public static void wakeUpScreen(String alertType, Context context) {
        // User disabled this feature?
        if (!AppPreferences.getWakeScreenEnabled(context)) {
            // Stop execution
            return;
        }

        // Alert popup enabled?
        if (AppPreferences.getPopupEnabled(context)) {
            // Stop execution, popup window already wakes up the screen
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

    public static void wakeUpScreen(Context context) {
        // Get power manager instance
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // Screen off?
        if (!powerManager.isScreenOn()) {
            // Turn on
            PowerManager.WakeLock wl = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "redalert:alert");

            try {
                // Release wakelock automatically after 3 seconds
                wl.acquire(3000);
            }
            catch (Exception exc) {
                // Ignore exceptions
            }
        }
    }
}
