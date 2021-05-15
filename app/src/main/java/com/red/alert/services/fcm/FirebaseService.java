package com.red.alert.services.fcm;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.communication.broadcasts.SelfTestEvents;
import com.red.alert.logic.communication.push.FCMPushParameters;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.Map;

public class FirebaseService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        // Already registered with API?
        if (RedAlertAPI.isRegistered(this)) {
            // Check whether FCM token has changed
            if (FCMRegistration.isRegistered(this) && !FCMRegistration.getRegistrationToken(this).equals(token)) {
                // Update token server-side
                new UpdateTokenAsync().execute(token);
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

        // Log it
        Log.e(Logging.TAG, "Received push via FCM gateway");

        // Receive the alert
        AlertLogic.processIncomingAlert(alertCities, alertType, this);
    }

    public class UpdateTokenAsync extends AsyncTaskAdapter<String, String, Exception> {
        @Override
        protected Exception doInBackground(String... params) {
            // Get new token as first param
            String token = params[0];

            try {
                // Update token server-side
                RedAlertAPI.updateToken(token, FirebaseService.this);
            }
            catch (Exception exc) {
                // Return exception to onPostExecute
                return exc;
            }

            // Success
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            // Failed?
            if (exc != null) {
                // Log failure
                Log.e(Logging.TAG, "Updating device token failed", exc);
            }
        }
    }
}
