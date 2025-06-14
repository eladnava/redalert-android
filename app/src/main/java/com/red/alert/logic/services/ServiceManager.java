package com.red.alert.logic.services;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.red.alert.config.push.PushyGateway;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.services.location.LocationService;

import me.pushy.sdk.Pushy;

public class ServiceManager {
    public static void startAppServices(Context context) {
        // Start the Pushy push service
        startPushyService(context);

        // Location alerts enabled?
        if (AppPreferences.getNotificationsEnabled(context) && AppPreferences.getLocationAlertsEnabled(context)) {
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

    public static void startPushyService(Context context) {
        // Set custom heartbeat interval before calling Pushy.listen()
        Pushy.setHeartbeatInterval(PushyGateway.SOCKET_HEARTBEAT_INTERVAL, context);

        // Enable variable keep alive
        Pushy.toggleVariableKeepAlive(true, context);

        // Enable/disable foreground service
        Pushy.toggleForegroundService(AppPreferences.getForegroundServiceEnabled(context), context);

        // Alerts enabled?
        if (AppPreferences.getNotificationsEnabled(context)) {
            // Start external service
            Pushy.listen(context);
        }
    }
}
