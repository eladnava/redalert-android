package com.red.alert.services.gcm;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.push.GCMPushParameters;
import com.red.alert.utils.communication.Broadcasts;

public class GCMPushReceiverService extends GcmListenerService
{
    @Override
    public void onMessageReceived(String from, Bundle data)
    {
        // Grab push data from extras
        String alertType = data.getString(GCMPushParameters.ALERT_TYPE);
        String alertZones = data.getString(GCMPushParameters.ALERT_AREAS);

        // Bad push?
        if (alertType == null || alertZones == null)
        {
            // Log it
            Log.e(Logging.TAG, "Malformed push received via GCM");

            // Stop execution to prevent crash
            return;
        }

        // Test alert?
        if (alertType.equals("test"))
        {
            // Log success
            Log.d(Logging.TAG, "GCM test passed");

            // Mark test as complete via broadcast
            Broadcasts.publish(this, SelfTestEvents.GCM_TEST_PASSED);

            // Stop execution (we don't want any visible notification)
            return;
        }

        // Receive the alert
        AlertLogic.processIncomingAlert(alertZones, alertType, this);
    }
}
