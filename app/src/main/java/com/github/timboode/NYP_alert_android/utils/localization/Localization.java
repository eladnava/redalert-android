package com.github.timboode.NYP_alert_android.utils.localization;

import android.content.Context;
import android.content.res.Configuration;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.utils.caching.Singleton;
import com.github.timboode.NYP_alert_android.utils.formatting.StringUtils;

import java.util.Locale;

import androidx.appcompat.app.AppCompatDelegate;

public class Localization {
    static Locale mDefaultLocale;

    public static boolean isRTLLocale(Context context) {
        // Override locale, if chosen
        overridePhoneLocale(context);

        // Check for Hebrew or Arabic locales
        return isHebrew(context) || isArabic(context);
    }

    public static void overridePhoneLocale(Context context) {
        // Preserve default locale
        if (mDefaultLocale == null) {
            mDefaultLocale = Locale.getDefault();
        }

        // Create new configuration
        Configuration configuration = context.getResources().getConfiguration();
        Configuration appConfiguration = context.getApplicationContext().getResources().getConfiguration();

        // Get new locale code
        String overrideLocale = Singleton.getSharedPreferences(context).getString(context.getString(R.string.langPref), "");

        // Chosen a new locale?
        if (!StringUtils.stringIsNullOrEmpty(overrideLocale)) {
            // Set it
            configuration.locale = new Locale(overrideLocale);
            appConfiguration.locale = new Locale(overrideLocale);
        }
        else {
            // Use default locale
            configuration.locale = Locale.getDefault();
            appConfiguration.locale = Locale.getDefault();
        }

        // Apply the configuration
        context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
        context.getApplicationContext().getResources().updateConfiguration(appConfiguration, context.getResources().getDisplayMetrics());
    }

    public static void applyThemeSelection(Context context) {
        // Load selected theme from SharedPreferences
        String selectedTheme = Singleton.getSharedPreferences(context).getString(context.getString(R.string.themePref), context.getString(R.string.lightThemeCode));

        // Automatic selected?
        if (selectedTheme.equals(context.getString(R.string.automaticThemeCode))) {
            // Follow system-wide setting
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        else if (selectedTheme.equals(context.getString(R.string.lightThemeCode))) {
            // Light theme
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        else if (selectedTheme.equals(context.getString(R.string.darkThemeCode))) {
            // Dark theme
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public static void restoreDefaultLocale(Context context) {
        // Create new configuration
        Configuration configuration = context.getResources().getConfiguration();
        Configuration appConfiguration = context.getApplicationContext().getResources().getConfiguration();

        // Use default locale
        configuration.locale = mDefaultLocale;
        appConfiguration.locale = mDefaultLocale;

        // Apply the configuration
        context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
        context.getApplicationContext().getResources().updateConfiguration(appConfiguration, context.getResources().getDisplayMetrics());
    }

    public static boolean isRussian(Context context) {
        // Check for russian locale
        return context.getResources().getConfiguration().locale.getLanguage().startsWith(context.getString(R.string.russianCode));
    }

    public static boolean isArabic(Context context) {
        // Check for Arabic locale
        return context.getResources().getConfiguration().locale.getLanguage().startsWith(context.getString(R.string.arabicCode));
    }

    public static boolean isHebrew(Context context) {
        // Check for Hebrew locale
        return context.getResources().getConfiguration().locale.getLanguage().startsWith(context.getString(R.string.hebrewCode)) || context.getResources().getConfiguration().locale.getLanguage().startsWith(context.getString(R.string.hebrewCode2));
    }

    public static String localizeDigits(String input, Context context) {
        // Arabic?
        if (Localization.isArabic(context)) {
            // Convert digits to Arabic numerals
            input = Localization.convertDigitsToArabicNumerals(input);
        }
        else {
            // Convert Arabic numerals to digits
            input = Localization.convertArabicNumeralsToDigits(input);
        }

        // Return normalized input
        return input;
    }

    public static String convertDigitsToArabicNumerals(String input) {
        // Arabic numeral chars (0-9)
        char[] arabicChars = {'٠','١','٢','٣','٤','٥','٦','٧','٨','٩'};

        // Output string
        StringBuilder builder = new StringBuilder();

        // Traverse original string
        for (int i = 0; i < input.length(); i++) {
            // Check for numeric digit
            if (Character.isDigit(input.charAt(i)) && arabicChars.length > (int)(input.charAt(i)) - 48) {
                // Convert to Arabic digit
                builder.append(arabicChars[(int) (input.charAt(i)) - 48]);
            }
            else {
                // Not a digit, add it as-is
                builder.append(input.charAt(i));
            }
        }

        // Build output string
        return builder.toString();
    }

    public static String convertArabicNumeralsToDigits(String input) {
        // Convert input to char array
        char[] chars = new char[input.length()];

        // Traverse chars
        for (int i = 0; i < input.length(); i++) {
            // Get current char
            char ch = input.charAt(i);

            // Check if it's an Arabic numeral
            if (ch >= 0x0660 && ch <= 0x0669)
                ch -= 0x0660 - '0';
            else if (ch >= 0x06f0 && ch <= 0x06F9)
                ch -= 0x06f0 - '0';

            // Overwrite char
            chars[i] = ch;
        }

        // Build output string
        return new String(chars);
    }
}
