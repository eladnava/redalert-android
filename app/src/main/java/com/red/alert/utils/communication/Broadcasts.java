package com.red.alert.utils.communication;

import android.content.Context;
import android.content.SharedPreferences;

import com.red.alert.utils.caching.Singleton;

public class Broadcasts
{
    public static void subscribe(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener)
    {
        // Listen for preference changes
        Singleton.getSharedPreferences(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unsubscribe(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener)
    {
        // Unsubscribe from preference changes
        Singleton.getSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static void publish(Context context, String property)
    {
        // Edit shared preferences
        SharedPreferences.Editor edit = Singleton.getSharedPreferences(context).edit();

        // Store current ms in settings with the given property,
        // so that listeners will be notified
        edit.putLong(property, System.currentTimeMillis());

        // Save and flush
        edit.commit();
    }
}
