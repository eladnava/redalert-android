package com.red.alert.utils.intents;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

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
}
