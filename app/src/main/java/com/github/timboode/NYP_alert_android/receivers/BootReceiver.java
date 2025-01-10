package com.github.timboode.NYP_alert_android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.timboode.NYP_alert_android.logic.services.ServiceManager;
import com.github.timboode.NYP_alert_android.logic.settings.AppPreferences;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // Got boot completed event?
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start the push service
            //ServiceManager.startPushyService(context);
            // TODO: Start push listener

            // Location alerts enabled?
            if (AppPreferences.getLocationAlertsEnabled(context)) {
                // Start the location service
                ServiceManager.startPushNotificationService(context);
            }
        }
    }
}
