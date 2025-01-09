package com.github.timboode.NYP_alert_android.receivers.pushy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertLogic;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.SelfTestEvents;
import com.github.timboode.NYP_alert_android.logic.communication.push.PushParameters;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;

public class PushyPushReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Grab push data from extras
        String alertId = intent.getStringExtra(PushParameters.ALERT_ID);
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
        if (alertType.equals(AlertTypes.TEST)) {
            // Log success
            Log.d(Logging.TAG, "Pushy test passed");

            // Mark test as complete via broadcast
            Broadcasts.publish(context, SelfTestEvents.PUSHY_TEST_PASSED);

            // Stop execution (we don't want any visible notification)
            return;
        }

        // Log it
        Log.d(Logging.TAG, "Received push via Pushy gateway");

        // Receive the alert
        AlertLogic.processIncomingAlert(threatType, alertCities, alertType, alertId, context);
    }
}