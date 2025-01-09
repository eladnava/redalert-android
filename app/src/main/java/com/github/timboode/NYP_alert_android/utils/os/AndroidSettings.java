package com.github.timboode.NYP_alert_android.utils.os;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.github.timboode.NYP_alert_android.R;

public class AndroidSettings {
    public static void openAppInfoPage(Context context) {
        // Open settings screen for this app
        Intent intent = new Intent();

        // App info page
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Set package to current package
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);

        // Start settings activity
        context.startActivity(intent);
    }

    public static String getBatteryOptimizationWhitelistInstructions(Context context) {
        // Special instructions for Samsung devices
        if (Build.MANUFACTURER.toLowerCase().contains("samsung") && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return context.getString(R.string.disableBatteryOptimizationsSamsungInstructions);
        }

        // Display stock Android instructions
        return context.getString(R.string.disableBatteryOptimizationsInstructions);
    }
}
