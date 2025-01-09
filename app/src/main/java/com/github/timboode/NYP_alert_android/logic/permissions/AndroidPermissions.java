package com.github.timboode.NYP_alert_android.logic.permissions;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class AndroidPermissions {
    public static boolean isNotificationPermissionGranted(Context context) {
        // For Android 13 (API level 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        // For older Android versions
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}
