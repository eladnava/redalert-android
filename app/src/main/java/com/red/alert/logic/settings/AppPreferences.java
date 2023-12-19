package com.red.alert.logic.settings;

import android.content.Context;

import com.red.alert.R;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.metadata.LocationData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPreferences {
    public static boolean getNotificationsEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.enabledPref), true);
    }

    public static boolean getLocationAlertsEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.locationAlertsPref), false);
    }

    public static boolean getSecondaryNotificationsEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.secondaryEnabledPref), false);
    }

    public static boolean getTutorialDisplayed(Context context) {
        // No regions/cities selected?
        if (AppPreferences.getNotificationsEnabled(context) && AppPreferences.getSubscriptions(context).size() == 0) {
            return false;
        }

        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.tutorialPref), false);
    }

    public static boolean getPopupEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.alertPopupPref), false);
    }

    public static boolean getForegroundServiceEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.foregroundServicePref), false);
    }

    public static boolean getWakeScreenEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.wakeScreenPref), true);
    }

    public static void setTutorialDisplayed(Context context) {
        // Update stored value
        Singleton.getSharedPreferences(context).edit().putBoolean(context.getString(R.string.tutorialPref), true).commit();
    }

    public static void enableAlertPopups(Context context) {
        // Update stored value
        Singleton.getSharedPreferences(context).edit().putBoolean(context.getString(R.string.alertPopupPref), true).commit();
    }

    public static long getLastSubscribedTimestamp(Context context) {
        // Fetch last subscribed timestamp
        return Singleton.getSharedPreferences(context).getLong(context.getString(R.string.lastSubscribed), 0);
    }

    public static void updateLastSubscribedTimestamp(long timestamp, Context context) {
        // Update last subscribed timestamp
        Singleton.getSharedPreferences(context).edit().putLong(context.getString(R.string.lastSubscribed), timestamp).commit();
    }

    public static long getRecentAlertsCutoffTimestamp(Context context) {
        // Fetch recent alerts cutoff timestamp
        return Singleton.getSharedPreferences(context).getLong(context.getString(R.string.recentAlertsCutoff), 0);
    }

    public static void updateRecentAlertsCutoffTimestamp(long timestamp, Context context) {
        // Update recent alerts cutoff timestamp
        Singleton.getSharedPreferences(context).edit().putLong(context.getString(R.string.recentAlertsCutoff), timestamp).commit();
    }

    public static void setCityLastAlertTime(String city, long timestamp, Context context) {
        // Update last alert timestamp for this city
        Singleton.getSharedPreferences(context).edit().putLong(city, timestamp).commit();
    }

    public static long getCityLastAlert(String city, Context context) {
        // Get last alert timestamp for this city
        return Singleton.getSharedPreferences(context).getLong(city, 0);
    }

    public static float getPrimaryAlertVolume(Context context, float overrideValue) {
        // Override?
        if (overrideValue != -1) {
            return overrideValue;
        }

        // Get stored value
        return Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.volumePref), 1.0f);
    }

    public static float getSecondaryAlertVolume(Context context, float overrideValue) {
        // Override?
        if (overrideValue != -1) {
            return overrideValue;
        }

        // Get stored value
        return Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.secondaryVolumePref), 1.0f);
    }

    public static List<String> getSubscriptions(Context context) {
        // Merged list of cities and regions
        List<String> subscriptions = new ArrayList<>();

        // Check if notifications disabled
        if (!getNotificationsEnabled(context)) {
            return subscriptions;
        }

        // Location alerts enabled?
        if (AppPreferences.getLocationAlertsEnabled(context)) {
            // Subscribe to all and check proximity client-side
            subscriptions.add("all");
        }

        // Get user's selected primary zones
        String selectedZones = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedZonesPref), context.getString(R.string.none));

        // Anything selected?
        if (!StringUtils.stringIsNullOrEmpty(selectedZones)) {
            // Explode selected zones into array and push into primarySubs
            subscriptions.addAll(LocationData.getEnglishZoneTopicNames(LocationData.explodePSV(selectedZones), context));
        } else {
            // Empty value means all regions
            subscriptions.add("all");
        }

        // Get user's selected cities
        String selectedCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedCitiesPref), context.getString(R.string.none));

        // Anything selected?
        if (!StringUtils.stringIsNullOrEmpty(selectedCities)) {
            // Explode selected cities into array and push into primarySubs
            subscriptions.addAll(LocationData.getEnglishCityTopicNames(LocationData.explodePSV(selectedCities), context));

        } else {
            // Empty value means all cities
            subscriptions.add("all");
        }

        // Check if secondary notifications enabled
        if (getSecondaryNotificationsEnabled(context)) {
            // Get user's secondary cities
            String secondaryCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedSecondaryCitiesPref), context.getString(R.string.none));

            // Anything selected?
            if (!StringUtils.stringIsNullOrEmpty(secondaryCities)) {
                // Explode selected cities into array and push into primarySubs
                subscriptions.addAll(LocationData.getEnglishCityTopicNames(LocationData.explodePSV(secondaryCities), context));
            } else {
                // Empty value means all cities
                subscriptions.add("all");
            }
        }

        // Remove duplicates
        subscriptions = cleanSubscriptions(subscriptions);

        // All done
        return subscriptions;
    }

    static List<String> cleanSubscriptions(List<String> subscriptions) {
        // Traverse items
        for (String item : subscriptions) {
            // "None" selected?
            if (item.equals("none")) {
                return new ArrayList<>();
            }
        }

        // Remove duplicate entries
        Set<String> set = new HashSet<>(subscriptions);

        // Clear and only add items from set
        subscriptions.clear();
        subscriptions.addAll(set);

        // Return original list
        return subscriptions;
    }
}
