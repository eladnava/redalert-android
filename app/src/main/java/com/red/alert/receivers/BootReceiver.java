package com.red.alert.receivers;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.legacy.content.WakefulBroadcastReceiver;

import com.red.alert.logic.services.ServiceManager;
import com.red.alert.logic.settings.AppPreferences;

public class BootReceiver extends WakefulBroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // Got boot completed event?
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start the Pushy push service
            ServiceManager.startPushyService(context);

            // Location alerts enabled?
            if (AppPreferences.getLocationAlertsEnabled(context)) {
                // Unfortunately, we can't start a foreground location service via boot receiver on API level 34+
                // Wait for users to manually open the app to restart the foreground service
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Start the location service
                    ServiceManager.startLocationService(context);
                }
            }
        }
    }
}
