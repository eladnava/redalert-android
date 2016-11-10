package com.red.alert.utils.localization;

import android.content.Context;
import android.content.res.Configuration;

import com.red.alert.R;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Localization {
    public static boolean isEnglishLocale(Context context) {
        // Override locale, if chosen
        overridePhoneLocale(context);

        // Get language code
        String languageCode = context.getResources().getConfiguration().locale.getLanguage();

        // English locale codes list
        List<String> englishLocales = new ArrayList<>();

        // Locale codes that are considered "English"
        englishLocales.add(context.getString(R.string.englishCode));
        englishLocales.add(context.getString(R.string.italianCode));

        // Traverse valid locale codes
        for (String code : englishLocales) {
            // Check for string equality
            if (languageCode.equals(code)) {
                return true;
            }
        }

        // Not an English locale
        return false;
    }

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
    }
}
