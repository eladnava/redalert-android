package com.red.alert.utils.compatibility;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;


public class DozeCompatibility
{
    public static boolean shouldDisableBatteryOptimizations(Context context)
    {
        // Check Android version (Doze mode is nonexistent before Marshmallow)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            return false;
        }

        // Get an instance of the system power manager
        PowerManager powerManager = (PowerManager) context.getSystemService(context.POWER_SERVICE);

        // Get app package name via context (com.red.alert)
        String packageName = context.getPackageName();

        // Already ignoring battery optimizations for this app?
        // This call only works on Android 23+ APIs
        if ( powerManager.isIgnoringBatteryOptimizations(packageName) )
        {
            return false;
        }

        // Tell the user to disable battery optimizations
        return true;
    }

    public static void requestDisableBatteryOptimizations(Context context)
    {
        // Prepare the request dialog intent
        Intent intent = new Intent();

        // Set package name and intent action
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));

        // Ask the user politely to disable battery optimizations
        context.startActivity(intent);
    }
}
