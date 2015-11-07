package com.red.alert.logic.settings;

import android.content.Context;

import com.red.alert.R;
import com.red.alert.utils.caching.Singleton;

public class AppPreferences
{
    public static boolean getNotificationsEnabled(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.enabledPref), true);
    }

    public static boolean getLocationAlertsEnabled(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.locationAlertsPref), false);
    }

    public static boolean getDisconnectedNotificationEnabled(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.disconnectedNotificationPref), false);
    }

    public static boolean getSecondaryNotificationsEnabled(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.secondaryEnabledPref), true);
    }

    public static boolean getMiBandIntegrationEnabled(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.miBandPref), false);
    }

    public static boolean getTutorialDisplayed(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.tutorialPref), false);
    }

    public static boolean getPopupEnabled(Context context)
    {
        // Get saved preference
        return Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.alertPopupPref), true);
    }

    public static void setTutorialDisplayed(Context context)
    {
        // Update stored value
        Singleton.getSharedPreferences(context).edit().putBoolean(context.getString(R.string.tutorialPref), true).commit();
    }


    public static float getPrimaryAlertVolume(Context context, float overrideValue)
    {
        // Override?
        if (overrideValue != -1)
        {
            return overrideValue;
        }

        // Get stored value
        return Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.volumePref), 1.0f);
    }

    public static float getSecondaryAlertVolume(Context context, float overrideValue)
    {
        // Override?
        if (overrideValue != -1)
        {
            return overrideValue;
        }

        // Get stored value
        return Singleton.getSharedPreferences(context).getFloat(context.getString(R.string.secondaryVolumePref), 1.0f);
    }
}
