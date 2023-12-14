package com.red.alert.utils.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.API;
import com.red.alert.config.Logging;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.req.UpdateNotificationsRequest;
import com.red.alert.model.req.RegistrationRequest;
import com.red.alert.model.req.SubscribeRequest;
import com.red.alert.model.req.UpdateTokenRequest;
import com.red.alert.model.res.GenericResponse;
import com.red.alert.model.res.RegistrationResponse;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedAlertAPI {
    public static void register(String fcmToken, String pushyToken, Context context) throws Exception {
        // Prepare an object to store and send the FCM device token to the API
        RegistrationRequest register = new RegistrationRequest(fcmToken, pushyToken, API.PLATFORM_IDENTIFIER);

        // Send the request to our API
        String response;

        try {
            // Execute request
            response = HTTP.post("/register", Singleton.getJackson().writeValueAsString(register));
        }
        catch (Exception exc) {
            // Log failure
            throw new Exception("Registration failed", exc);
        }

        // Convert response into JSON object
        RegistrationResponse user;

        try {
            // Convert JSON to object
            user = Singleton.getJackson().readValue(response, RegistrationResponse.class);
        }
        catch (Exception exc) {
            // Log error
            throw new Exception("Parsing registration response failed", exc);
        }

        // Bad response?
        if (user.id == 0 || StringUtils.stringIsNullOrEmpty(user.hash)) {
            // Log error
            throw new Exception("Parsing registration response failed: " + response);
        }

        // Persist FCM token locally
        FCMRegistration.saveRegistrationToken(context, fcmToken);

        // Persist Pushy token locally
        PushyRegistration.saveRegistrationToken(context, pushyToken);

        // Persist user ID and hash to SharedPreferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(context).edit();

        // Store user info
        editor.putLong(context.getString(R.string.userIdPref), user.id);
        editor.putString(context.getString(R.string.userHashPref), user.hash);

        // Save and flush to disk
        editor.commit();

        // Log success
        Log.d(Logging.TAG, "RedAlert user registration success: " + user.id);
    }

    public static boolean isRegistered(Context context) {
        // Check for positive user ID
        return getUserId(context) > 0;
    }

    public static boolean isSubscribed(Context context) {
        // Check for subscribed boolean flag
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.isSubscribedPref), false);
    }

    public static long getUserId(Context context) {
        // Query shared preferences
        return Singleton.getSharedPreferences(context).getLong(context.getString(R.string.userIdPref), 0);
    }

    public static String getUserHash(Context context) {
        // Query shared preferences
        return Singleton.getSharedPreferences(context).getString(context.getString(R.string.userHashPref), "");
    }

    public static void subscribe(Context context) throws Exception {
        List<String> primarySubscriptions = new ArrayList<>();
        List<String> secondarySubscriptions = new ArrayList<>();

        // Location alerts enabled?
        if (AppPreferences.getLocationAlertsEnabled(context)) {
            // Subscribe to all and check proximity client-side
            primarySubscriptions.add("all");
        }

        // Get user's selected primary zones
        String selectedZones = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedZonesPref), context.getString(R.string.none));

        // Anything selected?
        if (!StringUtils.stringIsNullOrEmpty(selectedZones)) {
            // Explode selected zones into array and push into primarySubs
            primarySubscriptions.addAll(LocationData.explodePSV(selectedZones));
        }
        else {
            // Empty value means all regions
            primarySubscriptions.add("all");
        }

        // Get user's selected cities
        String selectedCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedCitiesPref), context.getString(R.string.none));

        // Anything selected?
        if (!StringUtils.stringIsNullOrEmpty(selectedCities)) {
            // Explode selected cities into array and push into primarySubs
            primarySubscriptions.addAll(LocationData.explodePSV(selectedCities));
        }
        else {
            // Empty value means all cities
            primarySubscriptions.add("all");
        }

        // Get user's secondary cities
        String secondaryCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedSecondaryCitiesPref), context.getString(R.string.none));

        // Anything selected?
        if (!StringUtils.stringIsNullOrEmpty(secondaryCities)) {
            // Explode selected cities into array and push into secondarySubs
            secondarySubscriptions.addAll(LocationData.explodePSV(secondaryCities));
        }
        else {
            // Empty value means all cities
            secondarySubscriptions.add("all");
        }

        // Prepare an object to send a subscription request to the API
        SubscribeRequest subscribe = new SubscribeRequest(getUserId(context), getUserHash(context), cleanSubscriptions(primarySubscriptions), cleanSubscriptions(secondarySubscriptions));

        // Send the request to our API
        String responseJson;

        try {
            // Execute request
            responseJson = HTTP.post("/subscribe", Singleton.getJackson().writeValueAsString(subscribe));
        }
        catch (Exception exc) {
            // Log failure
            throw new Exception("Subscribe request failed", exc);
        }

        // Convert response into JSON object
        GenericResponse response;

        try {
            // Convert JSON to object
            response = Singleton.getJackson().readValue(responseJson, GenericResponse.class);
        }
        catch (Exception exc) {
            // Log error
            throw new Exception("Parsing subscribe response failed", exc);
        }

        // Failed?
        if (!response.success) {
            // Log error
            throw new Exception("Failed to subscribe to alerts: " + response.error);
        }

        // Persist subscription flag to SharedPreferences
        SharedPreferences.Editor editor = Singleton.getSharedPreferences(context).edit();

        // Set is_subscribed to true
        editor.putBoolean(context.getString(R.string.isSubscribedPref), true);

        // Save and flush to disk
        editor.commit();
    }

    public static void updateNotificationPreferences(Context context) throws Exception {
        // Prepare an object to send the notification update request to the API
        UpdateNotificationsRequest updateNotifications = new UpdateNotificationsRequest(getUserId(context), getUserHash(context), AppPreferences.getNotificationsEnabled(context), AppPreferences.getSecondaryNotificationsEnabled(context));

        // Send the request to our API
        String responseJson;

        try {
            // Execute request
            responseJson = HTTP.post("/update/notifications", Singleton.getJackson().writeValueAsString(updateNotifications));
        }
        catch (Exception exc) {
            // Log failure
            throw new Exception("Notification setting update failed", exc);
        }

        // Convert response into JSON object
        GenericResponse response;

        try {
            // Convert JSON to object
            response = Singleton.getJackson().readValue(responseJson, GenericResponse.class);
        }
        catch (Exception exc) {
            // Log error
            throw new Exception("Parsing notification preferences response failed", exc);
        }

        // Failed?
        if (!response.success) {
            // Log error
            throw new Exception("Failed to update notification preferences: " + response.error);
        }
    }

    public static void updatePushTokens(String fcmToken, String pushyToken, Context context) throws Exception {
        // Debug log
        Log.d(Logging.TAG, "Updating FCM & Pushy tokens...");

        // Prepare an object to send the new token to the API
        UpdateTokenRequest updateRequest = new UpdateTokenRequest(getUserId(context), getUserHash(context), fcmToken, pushyToken);

        // Send the request to our API
        String responseJson;

        try {
            // Execute request
            responseJson = HTTP.post("/update/token", Singleton.getJackson().writeValueAsString(updateRequest));
        }
        catch (Exception exc) {
            // Log failure
            throw new Exception("Push token update request failed", exc);
        }

        // Convert response into JSON object
        GenericResponse response;

        try {
            // Convert JSON to object
            response = Singleton.getJackson().readValue(responseJson, GenericResponse.class);
        }
        catch (Exception exc) {
            // Log error
            throw new Exception("Parsing push token request failed", exc);
        }

        // Failed?
        if (!response.success) {
            // Log error
            throw new Exception("Failed to update push tokens: " + response.error);
        }

        // Persist FCM token locally
        FCMRegistration.saveRegistrationToken(context, fcmToken);

        // Persist Pushy token locally
        PushyRegistration.saveRegistrationToken(context, pushyToken);

        // Log success
        Log.d(Logging.TAG, "FCM & Pushy token update success");
    }

    static List<String> cleanSubscriptions(List<String> subscriptions) {
        // Traverse items
        for (String item: subscriptions) {
            // All selected at least once?
            if (item.equals("all") || item.equals("")) {
                return Arrays.asList("all");
            }

            // "None" selected?
            if (item.equals("none")) {
                return new ArrayList<>();
            }
        }

        // Return original list
        return subscriptions;
    }
}
