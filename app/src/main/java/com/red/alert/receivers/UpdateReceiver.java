package com.red.alert.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.red.alert.config.Logging;
import com.red.alert.services.gcm.GCMRegistrationService;

public class UpdateReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Got package replaced event?
        if (intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            // Is it this package?
            if (intent.getData().getSchemeSpecificPart().equals(context.getPackageName())) {
                // Log it
                Log.d(Logging.TAG, "App updated");

                // Call the intent service to re-register the device
                Intent gcmIntent = new Intent(context, GCMRegistrationService.class);

                // Start service (since this may take a while)
                context.startService(gcmIntent);
            }
        }
    }
}
