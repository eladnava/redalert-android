package com.red.alert.logic.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.red.alert.R;
import com.red.alert.config.API;
import com.red.alert.config.push.GCMGateway;
import com.red.alert.config.Logging;
import com.red.alert.model.req.RegistrationRequest;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.integration.GooglePlayServices;
import com.red.alert.utils.networking.HTTP;

public class GCMRegistration
{
    public static void registerForPushNotifications(Context context) throws Exception
    {
        // Make sure we have Google Play Services
        if (!GooglePlayServices.isAvailable(context))
        {
            // Throw exception
            throw new Exception(context.getString(R.string.noGooglePlayServices));
        }

        // Get instance ID API
        InstanceID instanceID = InstanceID.getInstance(context);

        // Get a GCM registration token
        String token = instanceID.getToken(GCMGateway.SENDER_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

        // Log to logcat
        Log.d(Logging.TAG, "GCM registration success: " + token);

        // Get GCM PubSub handler
        GcmPubSub pubSub = GcmPubSub.getInstance(context);

        // Subscribe to alerts topic
        // (limited to 1M subscriptions app-wide - think about how to scale this when the time comes)
        pubSub.subscribe(token, GCMGateway.ALERTS_TOPIC, null);

        // Log it
        Log.d(Logging.TAG, "GCM subscription success: " + GCMGateway.ALERTS_TOPIC);

        // Prepare an object to store and send the registration token to our API
        RegistrationRequest register = new RegistrationRequest(token, API.PLATFORM_IDENTIFIER);

        // Send the request to our API
        HTTP.post(API.API_ENDPOINT + "/register", Singleton.getJackson().writeValueAsString(register));

        // Persist it locally
        saveRegistrationToken(context, token);
    }

    public static String getRegistrationToken(Context context)
    {
        // Get it from SharedPreferences (may be null)
        return Singleton.getSharedPreferences(context).getString(context.getString(R.string.gcmTokenPref), null);
    }

    public static boolean isRegistered(Context context)
    {
        // Check whether it's null (in which case we never successfully registered)
        return getRegistrationToken(context) != null;
    }

    public static void saveRegistrationToken(Context context, String registrationToken)
    {
        // Edit shared preferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(context).edit();

        // Store boolean value
        editor.putString(context.getString(R.string.gcmTokenPref), registrationToken);

        // Save and flush
        editor.commit();
    }
}
