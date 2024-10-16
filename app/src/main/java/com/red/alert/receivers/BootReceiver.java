package com.red.alert.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.red.alert.logic.services.ServiceManager;
import com.red.alert.logic.settings.AppPreferences;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // Got boot completed event?
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start the Pushy push service
            ServiceManager.startPushyService(context);

            // Location alerts enabled?
            if (AppPreferences.getLocationAlertsEnabled(context)) {
                // Start the location service
                ServiceManager.startLocationService(context);
            }
        }
    }
}
