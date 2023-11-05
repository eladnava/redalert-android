package com.red.alert.logic.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.config.push.FCMGateway;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.networking.HTTP;

import java.util.List;

import androidx.annotation.NonNull;
import me.pushy.sdk.lib.jackson.core.type.TypeReference;

public class FCMRegistration {
    public static String registerForPushNotifications(final Context context) throws Exception {
        // Get an FCM registration token
        final String token = FirebaseInstanceId.getInstance().getToken(FCMGateway.SENDER_ID, FCMGateway.SCOPE);

        // Persist FCM token locally
        saveRegistrationToken(context, token);

        // Log to logcat
        Log.d(Logging.TAG, "FCM registration success: " + token);

        // Return token for saving and processing
        return token;
    }

    public static String getRegistrationToken(Context context) {
        // Get it from SharedPreferences (may be null)
        return Singleton.getSharedPreferences(context).getString(context.getString(R.string.fcmTokenPref), null);
    }

    public static boolean isRegistered(Context context) {
        // Check whether it's null (in which case we never successfully registered)
        return getRegistrationToken(context) != null;
    }

    public static void saveRegistrationToken(Context context, String registrationToken) {
        // Edit shared preferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(context).edit();

        // Store boolean value
        editor.putString(context.getString(R.string.fcmTokenPref), registrationToken);

        // Save and flush
        editor.commit();
    }

    public static List<String> getCurrentFCMSubscriptions(Context context) throws Exception {
        // Store JSON as string initially
        String subscriptionsJSON;

        try {
            // Send the request to our API
            subscriptionsJSON = HTTP.post("/subscriptions?token=" + getRegistrationToken(context), "{}");
        } catch (Exception exc) {
            // Non-recoverable error
            throw new Exception("Get subscriptions request failed", exc);
        }

        // Prepare list of subscriptions
        List<String> subscriptions;

        try {
            // Convert JSON to object
            subscriptions = Singleton.getJackson().readValue(subscriptionsJSON, new TypeReference<List<String>>() {
            });
        } catch (Exception exc) {
            // Non-recoverable error
            throw new Exception("Get subscriptions request failed", exc);
        }

        // Return list
        return subscriptions;
    }

    public static void updateSubscriptions(Context context) throws Exception {
        // Make sure registered for notifications
        if (!FCMRegistration.isRegistered(context)) {
            return;
        }

        // FCM InstanceID API returns cached subscriptions for up to X seconds
        // Ensure enough time passed before the last time we updated the FCM subscriptions
        while (AppPreferences.getLastSubscribedTimestamp(context) > DateTime.getUnixTimestamp() - FCMGateway.FCM_SUBSCRIPTIONS_CACHE_TIME) {
            Thread.sleep(500);
        }

        // Get list of existing subscriptions
        List<String> existingSubscriptions = FCMRegistration.getCurrentFCMSubscriptions(context);

        // Log for debugging purposes
        Log.d(Logging.TAG, "Currently subscribed to: [" + TextUtils.join(", ", existingSubscriptions) + "]");

        // Get new subscriptions list
        List<String> subscriptions = AppPreferences.getSubscriptions(context);

        // Subscribe to new topics
        for (final String topic : subscriptions) {
            // Skip topics we're already subscribed to
            if (existingSubscriptions.contains(topic)) {
                continue;
            }

            // Subscribe to topic
            Task subscribe = FirebaseMessaging.getInstance().subscribeToTopic(topic)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (!task.isSuccessful()) {
                                Log.e(Logging.TAG, "FCM subscribe failed: ", task.getException());
                                return;
                            }

                            // Log it
                            Log.d(Logging.TAG, "FCM subscribe success: " + topic);
                        }
                    });

            // Wait for task to finish
            while (!subscribe.isComplete()) {
                Thread.sleep(50);
            }

            // Failed?
            if (!subscribe.isSuccessful()) {
                throw new Exception("Failed to subscribe to topic: " + topic);
            }
        }

        // Unsubscribe from old topics
        for (final String topic : existingSubscriptions) {
            // Skip topics we're wanting to stay subscribed to
            if (subscriptions.contains(topic)) {
                continue;
            }

            // Unsubscribe from old topic
            Task unsubscribe = FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (!task.isSuccessful()) {
                                Log.e(Logging.TAG, "FCM unsubscribe failed: ", task.getException());
                                return;
                            }

                            // Log it
                            Log.d(Logging.TAG, "FCM unsubscribe success: " + topic);
                        }
                    });

            // Wait for task to finish
            while (!unsubscribe.isComplete()) {
                Thread.sleep(50);
            }

            // Failed?
            if (!unsubscribe.isSuccessful()) {
                throw new Exception("Failed to unsubscribe from topic: " + topic);
            }
        }

        // Update FCM last subscribed timestamp
        AppPreferences.updateLastSubscribedTimestamp(DateTime.getUnixTimestamp(), context);
    }
}
