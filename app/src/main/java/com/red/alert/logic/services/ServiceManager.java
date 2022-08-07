package com.red.alert.logic.services;

import android.content.Context;
import android.content.Intent;

import com.red.alert.config.push.PushyGateway;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.services.location.LocationService;

import me.pushy.sdk.Pushy;

public class ServiceManager {
    public static void startAppServices(Context context) {
        // Start the Pushy push service
        startPushyService(context);

        // Start the location service
        startLocationService(context);
    }

    public static void startLocationService(Context context) {
        // Can we request location?
        if (!LocationLogic.shouldRequestLocationUpdates(context)) {
            return;
        }

        try {
            // Start the location service
            context.startService(new Intent(context, LocationService.class));
        }
        catch (Exception exc) {
            // May fail if called from BootReceiver
        }
    }

    public static void startPushyService(Context context) {
        // Set custom heartbeat interval before calling Pushy.listen()
        Pushy.setHeartbeatInterval(PushyGateway.SOCKET_HEARTBEAT_INTERVAL, context);

        // Start external service
        Pushy.listen(context);
    }
}
