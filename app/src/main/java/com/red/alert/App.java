package com.red.alert;

import android.app.Application;

import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.utils.localization.Localization;

import me.pushy.sdk.Pushy;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Reapply locale
        Localization.overridePhoneLocale(this);

        // Enable/disable foreground service
        Pushy.toggleForegroundService(AppPreferences.getForegroundServiceEnabled(this), this);
    }
}
