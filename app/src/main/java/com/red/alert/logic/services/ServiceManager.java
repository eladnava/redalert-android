package com.red.alert.logic.services;

import android.content.Context;
import android.content.Intent;

import com.red.alert.config.push.PushyGateway;

import me.pushy.sdk.Pushy;

public class ServiceManager {
    public static void startAppServices(Context context) {
        // Start the Pushy push service
        startPushyService(context);
    }

    public static void startPushyService(Context context) {
        // Set custom heartbeat interval before calling Pushy.listen()
        Pushy.setHeartbeatInterval(PushyGateway.SOCKET_HEARTBEAT_INTERVAL, context);

        // Start external service
        Pushy.listen(context);
    }
}
