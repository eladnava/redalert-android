package com.github.timboode.NYP_alert_android.logic.services;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.github.timboode.NYP_alert_android.config.push.PushyGateway;
import com.github.timboode.NYP_alert_android.logic.location.LocationLogic;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;
import com.github.timboode.NYP_alert_android.services.location.LocationService;

public class ServiceManager {
    public static void startAppServices(Context context) {

        // Location alerts enabled?
        if (AppPreferences.getLocationAlertsEnabled(context)) {
            // Start the location service
            ServiceManager.startLocationService(context);
        }
    }

    public static void startLocationService(Context context) {
        // Check if all prerequisites are met
        if (!LocationLogic.canStartForegroundLocationService(context)) {
            return;
        }

        try {
            // Use foreground service on Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Start the location service in foreground mode
                context.startForegroundService(new Intent(context, LocationService.class));
            }
            else {
                // Start the location service
                context.startService(new Intent(context, LocationService.class));
            }
        }
        catch (Exception exc) {
            // May fail if called from BootReceiver
        }
    }

    public static void stopLocationService(Context context) {
        // Stop the location service
        context.stopService(new Intent(context, LocationService.class));
    }
}
