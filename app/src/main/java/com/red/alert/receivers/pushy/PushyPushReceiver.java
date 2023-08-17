package com.red.alert.receivers.pushy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.push.PushParameters;
import com.red.alert.utils.communication.Broadcasts;

public class PushyPushReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Grab push data from extras
        String alertType = intent.getStringExtra(PushParameters.ALERT_TYPE);
        String threatType = intent.getStringExtra(PushParameters.THREAT_TYPE);
        String alertCities = intent.getStringExtra(PushParameters.ALERT_CITIES);

        // Bad push?
        if (alertType == null || alertCities == null) {
            // Log it
            Log.e(Logging.TAG, "Malformed push received via Pushy");

            // Stop execution to prevent crash
            return;
        }

        // Test alert?
        if (alertType.equals("test")) {
            // Log success
            Log.d(Logging.TAG, "Pushy test passed");

            // Mark test as complete via broadcast
            Broadcasts.publish(context, SelfTestEvents.PUSHY_TEST_PASSED);

            // Stop execution (we don't want any visible notification)
            return;
        }

        // Log it
        Log.e(Logging.TAG, "Received push via Pushy gateway");

        // Receive the alert
        AlertLogic.processIncomingAlert(threatType, alertCities, alertType, context);
    }
}