package com.red.alert.receivers;

import android.content.Context;
import android.content.Intent;
import androidx.legacy.content.WakefulBroadcastReceiver;

import com.red.alert.logic.services.ServiceManager;

public class BootReceiver extends WakefulBroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // Got boot completed event?
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Run all other services
            ServiceManager.startAppServices(context);
        }
    }
}
