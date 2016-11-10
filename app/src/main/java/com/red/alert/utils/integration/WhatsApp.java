package com.red.alert.utils.integration;

import android.content.Context;
import android.content.pm.PackageManager;

import com.red.alert.config.Integrations;

public class WhatsApp {
    public static boolean isAppInstalled(Context context) {
        // Get package manager
        PackageManager packageManager = context.getPackageManager();

        try {
            // Locate package by name
            packageManager.getPackageInfo(Integrations.WHATSAPP_PACKAGE, PackageManager.GET_ACTIVITIES);

            // If we are still here, the app exists
            return true;
        }
        catch (Exception exc) {
            // No such package
            return false;
        }
    }
}
