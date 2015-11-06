package com.red.alert.logic.integration;

import android.content.Context;
import android.util.Log;

import com.betomaluje.miband.ActionCallback;
import com.betomaluje.miband.MiBand;
import com.red.alert.config.Logging;

public class BluetoothIntegration
{
    public static void notifyDevices(String alertType, String zone, Context context)
    {
        // Vibrate + LED for Mi Band (if enabled)
        notifyMiBand(context);
    }

    private static void notifyMiBand(Context context)
    {
        // Get an instance of the Mi Band library
        final MiBand miBand = MiBand.getInstance(context);

        // Attempt to connect to it
        miBand.connect(new ActionCallback()
        {
            @Override
            public void onSuccess(Object data)
            {
                // Log it
                Log.d(Logging.TAG, "Connected to Mi Band");

                // Set red LED color (determined via MiBandExample color picker)
                int ledColor = -64746;

                // Repeat the vibration + color
                int repeatTimes = 3;

                // Sleep in between each notification
                int sleepInterval = 2000;

                // Send the notification commands repeatedly
                miBand.notifyBandRepeated(ledColor, repeatTimes, sleepInterval);
            }

            @Override
            public void onFail(int errorCode, String msg)
            {
                // Log fail
                Log.d(Logging.TAG, "Failed to connect to Mi Band: " + msg);
            }
        });
    }
}
