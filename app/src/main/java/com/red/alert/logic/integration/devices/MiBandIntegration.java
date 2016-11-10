package com.red.alert.logic.integration.devices;

import android.content.Context;
import android.util.Log;

import com.betomaluje.miband.ActionCallback;
import com.betomaluje.miband.MiBand;
import com.red.alert.config.Logging;
import com.red.alert.logic.settings.AppPreferences;

public class MiBandIntegration {
    public static void notifyMiBand(final Context context) {
        // Xiaomi Mi Band integration enabled?
        if (!AppPreferences.getMiBandIntegrationEnabled(context)) {
            // Stop execution
            return;
        }

        // Get an instance of the Mi Band SDK
        MiBand miBand = MiBand.getInstance(context);

        // Attempt to connect to it
        miBand.connect(new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                // Log it
                Log.d(Logging.TAG, "Connected to Mi Band");

                // Vibration + LED colors
                sendNotificationCommands(context);
            }

            @Override
            public void onFail(int errorCode, String msg) {
                // Log fail
                Log.d(Logging.TAG, "Failed to connect to Mi Band: " + msg);
            }
        });
    }

    public static void sendNotificationCommands(Context context) {
        // Get an instance of the Mi Band SDK
        MiBand miBand = MiBand.getInstance(context);

        // Not connected for some reason?
        if (!miBand.isConnected()) {
            // Log it
            Log.e(Logging.TAG, "Mi Band is no longer connected!");
        }

        // Set red LED color (determined via MiBandExample color picker)
        int ledColor = -64746;

        // Repeat the vibration + color
        int repeatTimes = 3;

        // Sleep in between each notification
        int sleepInterval = 2000;

        // Send the notification commands repeatedly
        miBand.notifyBandRepeated(ledColor, repeatTimes, sleepInterval);
    }
}
