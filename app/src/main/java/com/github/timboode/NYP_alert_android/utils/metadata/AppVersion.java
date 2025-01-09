package com.github.timboode.NYP_alert_android.utils.metadata;

import android.content.Context;
import android.content.pm.PackageInfo;

public class AppVersion {
    public static int getVersionCode(Context context) {
        try {
            // Try to query for the package
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            // Return version code
            return packageInfo.versionCode;
        }
        catch (Exception exc) {
            // Return fallback version
            return -1;
        }
    }

    public static String getVersionName(Context context) {
        try {
            // Try to query for the package
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            // Return version code
            return packageInfo.versionName;
        }
        catch (Exception exc) {
            // Return fallback version
            return "";
        }
    }
}
