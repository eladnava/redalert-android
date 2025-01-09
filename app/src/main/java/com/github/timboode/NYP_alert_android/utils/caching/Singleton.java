package com.github.timboode.NYP_alert_android.utils.caching;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Singleton {
    static SharedPreferences mSettings;

    public static SharedPreferences getSharedPreferences(Context context) {
        // First time?
        if (mSettings == null) {
            // Open shared preferences
            mSettings = PreferenceManager.getDefaultSharedPreferences(context);
        }

        // Return cached object
        return mSettings;
    }
}
