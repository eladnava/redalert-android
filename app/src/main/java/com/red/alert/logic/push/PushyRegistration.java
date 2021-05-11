package com.red.alert.logic.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.config.push.PushyGateway;
import com.red.alert.utils.caching.Singleton;

import me.pushy.sdk.Pushy;

public class PushyRegistration {
    public static void registerForPushNotifications(Context context) throws Exception {
        // Acquire a unique registration ID for this device
        String token = Pushy.register(context);

        // Log to logcat
        Log.d(Logging.TAG, "Pushy registration success: " + token);

        // Subscribe to global alerts topic
        Pushy.subscribe(PushyGateway.ALERTS_TOPIC, context);

        // Log it
        Log.d(Logging.TAG, "Pushy subscribe success: " + PushyGateway.ALERTS_TOPIC);


        // Persist it locally (no need to send it to our API)
        saveRegistrationToken(context, token);
    }

    public static String getRegistrationToken(Context context) {
        // Get it from SharedPreferences (may be null)
        return Singleton.getSharedPreferences(context).getString(context.getString(R.string.pushyTokenPref), null);
    }

    public static boolean isRegistered(Context context) {
        // Check whether it's null (in which case we never successfully registered)
        return getRegistrationToken(context) != null;
    }

    public static void saveRegistrationToken(Context context, String registrationToken) {
        // Edit shared preferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(context).edit();

        // Store boolean value
        editor.putString(context.getString(R.string.pushyTokenPref), registrationToken);

        // Save and flush
        editor.commit();
    }
}
