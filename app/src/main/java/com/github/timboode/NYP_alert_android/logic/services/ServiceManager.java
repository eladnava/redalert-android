package com.github.timboode.NYP_alert_android.logic.services;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;

public class ServiceManager {
    public static void startAppServices(Context context) {

        // Location alerts enabled?
        if (AppPreferences.getLocationAlertsEnabled(context)) {
            // Start the location service
            ServiceManager.startPushNotificationService(context);
        }
    }

    public static void startPushNotificationService(Context context) {
        try {
            // Use foreground service on Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Start the location service in foreground mode
                context.startForegroundService(new Intent(context, IncomingMessageService.class));
            }
            else {
                // Start the location service
                context.startService(new Intent(context, IncomingMessageService.class));
            }
        }
        catch (Exception exc) {
            // May fail if called from BootReceiver
        }
    }

    public static void stopLocationService(Context context) {
        // Stop the location service
        context.stopService(new Intent(context, IncomingMessageService.class));
    }
}
