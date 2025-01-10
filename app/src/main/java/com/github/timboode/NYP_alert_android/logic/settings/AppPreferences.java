package com.github.timboode.NYP_alert_android.logic.settings;

import android.content.Context;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;

import java.util.ArrayList;
import java.util.List;

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

    public static boolean getSecondaryPopupEnabled(Context context) {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.secondaryAlertPopupPref), false);
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

    public static long getRecentAlertsCutoffTimestamp(Context context) {
        // Fetch recent alerts cutoff timestamp
        return Singleton.getSharedPreferences(context).getLong(context.getString(R.string.recentAlertsCutoff), 0);
    }

    public static void updateRecentAlertsCutoffTimestamp(long timestamp, Context context) {
        // Update recent alerts cutoff timestamp
        Singleton.getSharedPreferences(context).edit().putLong(context.getString(R.string.recentAlertsCutoff), timestamp).commit();
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

        // TODO: Get enabled subscriptions from SharedPreferences

        // All done
        return subscriptions;
    }
}
