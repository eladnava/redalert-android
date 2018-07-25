package com.red.alert.utils.caching;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import me.pushy.sdk.lib.jackson.databind.DeserializationFeature;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class Singleton {
    static ObjectMapper mMapper;
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

    public static ObjectMapper getJackson() {
        // First time?
        if (mMapper == null) {
            // Get Jackson instance
            mMapper = new ObjectMapper();

            // Ignore unknown properties
            mMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        // Return cached object
        return mMapper;
    }
}
