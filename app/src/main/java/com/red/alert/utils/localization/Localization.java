package com.red.alert.utils.localization;

import android.content.Context;
import android.content.res.Configuration;

import com.red.alert.R;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;

import java.util.Locale;

public class Localization {
    public static boolean isHebrewLocale(Context context) {
        // Override locale, if chosen
        overridePhoneLocale(context);

        // Get language code
        String languageCode = context.getResources().getConfiguration().locale.getLanguage();

        // Detect using locale code
        return languageCode.equals(context.getString(R.string.hebrewCode)) || languageCode.equals(context.getString(R.string.hebrewCode2));
    }

    public static void overridePhoneLocale(Context context) {
        // Create new configuration
        Configuration configuration = context.getResources().getConfiguration();

        // Get new locale code
        String overrideLocale = Singleton.getSharedPreferences(context).getString(context.getString(R.string.langPref), "");

        // Chosen a new locale?
        if (!StringUtils.stringIsNullOrEmpty(overrideLocale)) {
            // Set it
            configuration.locale = new Locale(overrideLocale);
        }
        else {
            // Use default locale
            configuration.locale = Locale.getDefault();
        }

        // Apply the configuration
        context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
        context.getApplicationContext().getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
    }

    public static boolean isRussian(Context context) {
        // Check for russian locale
        return context.getResources().getConfiguration().locale.getLanguage().equals(context.getString(R.string.russianCode));
    }
}
