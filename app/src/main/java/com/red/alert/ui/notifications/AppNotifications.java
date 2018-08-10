package com.red.alert.ui.notifications;

import android.app.NotificationManager;
import android.content.Context;

import com.red.alert.R;
import com.red.alert.services.sound.StopSoundService;

public class AppNotifications {
    public static void clearAll(Context context) {
        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Cancel all notifications
        notificationManager.cancelAll();

        // Stop alert sound if playing
        StopSoundService.stopSoundService(context);
    }
}
