package com.red.alert.services.fcm;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.push.FCMPushParameters;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.utils.communication.Broadcasts;

import java.util.Map;

public class FirebaseService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        // Check whether FCM is already registered and token was changed
        if (FCMRegistration.isRegistered(this) && !FCMRegistration.getRegistrationToken(this).equals(token)) {
            try {
                // Refresh the registration ID
                FCMRegistration.registerForPushNotifications(this);
            } catch (Exception exc) {
                // Log it
                Log.e(Logging.TAG, "FCM token update failed", exc);
            }
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Get payload map
        Map<String, String> data = remoteMessage.getData();

        // Grab push data from extras
        String alertType = data.get(FCMPushParameters.ALERT_TYPE);
        String alertCities = data.get(FCMPushParameters.ALERT_CITIES);

        // Bad push?
        if (alertType == null || alertCities == null) {
            // Log it
            Log.e(Logging.TAG, "Malformed push received via FCM");

            // Stop execution to prevent crash
            return;
        }

        // Test alert?
        if (alertType.equals("test")) {
            // Log success
            Log.d(Logging.TAG, "FCM test passed");

            // Mark test as complete via broadcast
            Broadcasts.publish(this, SelfTestEvents.FCM_TEST_PASSED);

            // Stop execution (we don't want any visible notification)
            return;
        }

        // Receive the alert
        AlertLogic.processIncomingAlert(alertCities, alertType, this);
    }
}
