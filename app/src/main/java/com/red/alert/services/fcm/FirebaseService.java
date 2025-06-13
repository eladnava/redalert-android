package com.red.alert.services.fcm;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.push.PushParameters;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.communication.Broadcasts;

import java.util.Map;

public class FirebaseService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String fcmToken) {
        // Debug log
        Log.d(Logging.TAG, "FCM onNewToken() called");

        // Already registered with API?
        if (RedAlertAPI.isRegistered(this)) {
            // Check whether FCM token has changed
            if (FCMRegistration.isRegistered(this) && !FCMRegistration.getRegistrationToken(this).equals(fcmToken)) {
                try {
                    // Get most up-to-date Pushy device token
                    String pushyToken = PushyRegistration.registerForPushNotifications(FirebaseService.this);

                    // Update tokens server-side
                    RedAlertAPI.updatePushTokens(fcmToken, pushyToken, FirebaseService.this);
                }
                catch (Exception exc) {
                    // Log failure
                    Log.e(Logging.TAG, "Updating device token failed", exc);
                }
            }
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Get payload map
        Map<String, String> data = remoteMessage.getData();

        // Grab push data from extras
        String alertId = data.get(PushParameters.ALERT_ID);
        String alertType = data.get(PushParameters.ALERT_TYPE);
        String threatType = data.get(PushParameters.THREAT_TYPE);
        String alertCities = data.get(PushParameters.ALERT_CITIES);
        String instructions = data.get(PushParameters.ALERT_INSTRUCTIONS);

        // Bad push?
        if (alertType == null || alertCities == null) {
            // Log it
            Log.e(Logging.TAG, "Malformed push received via FCM");

            // Stop execution to prevent crash
            return;
        }

        // Test alert?
        if (alertType.equals(AlertTypes.TEST)) {
            // Log success
            Log.d(Logging.TAG, "FCM test passed");

            // Mark test as complete via broadcast
            Broadcasts.publish(this, SelfTestEvents.FCM_TEST_PASSED);

            // Stop execution (we don't want any visible notification)
            return;
        }

        // Log it
        Log.d(Logging.TAG, "Received push via FCM gateway");

        // Receive the alert
        AlertLogic.processIncomingAlert(threatType, alertCities, alertType, alertId, instructions, this);
    }
}
