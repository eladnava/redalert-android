package com.red.alert.logic.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.red.alert.R;
import com.red.alert.config.API;
import com.red.alert.config.Logging;
import com.red.alert.config.push.FCMGateway;
import com.red.alert.model.req.RegistrationRequest;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.integration.GooglePlayServices;
import com.red.alert.utils.networking.HTTP;

import androidx.annotation.NonNull;

public class FCMRegistration {
    public static void registerForPushNotifications(final Context context) throws Exception {
        // Make sure we have Google Play Services
        if (!GooglePlayServices.isAvailable(context)) {
            // Throw exception
            throw new Exception(context.getString(R.string.noGooglePlayServices));
        }

        // Get an FCM registration token
        final String token = FirebaseInstanceId.getInstance().getToken(FCMGateway.SENDER_ID, FCMGateway.SCOPE);

        // Log to logcat
        Log.d(Logging.TAG, "FCM registration success: " + token);

        // Prepare an object to store and send the registration token to our API
        RegistrationRequest register = new RegistrationRequest(token, API.PLATFORM_IDENTIFIER);

        // Send the request to our API
        HTTP.post(API.API_ENDPOINT + "/register", Singleton.getJackson().writeValueAsString(register));

        // Subscribe to alerts topic
        FirebaseMessaging.getInstance().subscribeToTopic(FCMGateway.ALERTS_TOPIC)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!task.isSuccessful()) {
                            Log.e(Logging.TAG, "FCM subscribe failed: ", task.getException());
                            return;
                        }

                        // Log it
                        Log.d(Logging.TAG, "FCM subscribe success: " + FCMGateway.ALERTS_TOPIC);

                        // Persist token locally
                        saveRegistrationToken(context, token);
                    }
                });
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
}
