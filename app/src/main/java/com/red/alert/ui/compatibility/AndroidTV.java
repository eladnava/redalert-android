package com.red.alert.ui.compatibility;

import android.content.Context;
import android.content.pm.PackageManager;

public class AndroidTV {
    public static boolean isAndroidTV(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
