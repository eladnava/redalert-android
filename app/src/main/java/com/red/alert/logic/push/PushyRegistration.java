package com.red.alert.logic.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;

import java.util.List;

import me.pushy.sdk.Pushy;

public class PushyRegistration {
    public static String registerForPushNotifications(Context context) throws Exception {
        // Acquire a unique registration ID for this device
        String token = Pushy.register(context);

        // Save token
        saveRegistrationToken(context, token);

        // Log to logcat
        Log.d(Logging.TAG, "Pushy registration success: " + token);

        // Return token to be sent to API
        return token;
    }

    public static String getRegistrationToken(Context context) {
        // Get it from SharedPreferences (may be null)
        return Singleton.getSharedPreferences(context).getString(context.getString(R.string.pushyTokenPref), "");
    }

    public static boolean isRegistered(Context context) {
        // Check whether it's null (in which case we never successfully registered)
        return !StringUtils.stringIsNullOrEmpty(getRegistrationToken(context));
    }

    public static void saveRegistrationToken(Context context, String registrationToken) {
        // Edit shared preferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(context).edit();

        // Store boolean value
        editor.putString(context.getString(R.string.pushyTokenPref), registrationToken);

        // Save and flush
        editor.commit();
    }

    public static void updateSubscriptions(Context context) throws Exception {
        // Make sure registered for notifications
        if (!PushyRegistration.isRegistered(context)) {
            return;
        }

        // Unsubscribe from all
        Pushy.unsubscribe("*", context);

        // Get new subscriptions list
        List<String> subscriptions = AppPreferences.getSubscriptions(context);

        // Get total subscription count
        int totalSubscriptions = subscriptions.size();

        // Got any?
        if (totalSubscriptions > 0) {
            // More than 100?
            if (totalSubscriptions > 100) {
                // Split into batches
                for (int i = 0; i < totalSubscriptions; i += 100) {
                    // Create a sublist of size batchSize or smaller if it's the last batch
                    Pushy.subscribe(subscriptions.subList(i, Math.min(i + 100, totalSubscriptions)).toArray(new String[0]), context);
                }
            }
            else {
                // Subscribe to new topics (<= 100)
                Pushy.subscribe(subscriptions.toArray(new String[0]), context);
            }

            // Log it
            Log.d(Logging.TAG, "Pushy subscribe success: " + TextUtils.join(", ", subscriptions));
        }
        else {
            // Log it
            Log.d(Logging.TAG, "Pushy unsubscribed from all");
        }
    }
}
