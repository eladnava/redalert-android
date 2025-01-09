package com.github.timboode.NYP_alert_android.ui.notifications;

import android.app.NotificationManager;
import android.content.Context;

import com.github.timboode.NYP_alert_android.logic.feedback.sound.SoundLogic;

public class AppNotifications {
    public static void clearAll(Context context) {
        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Cancel all notifications
        notificationManager.cancelAll();

        // Stop alert sound if playing
        SoundLogic.stopSound(context);
    }
}
