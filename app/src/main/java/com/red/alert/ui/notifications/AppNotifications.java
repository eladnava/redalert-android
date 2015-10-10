package com.red.alert.ui.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.services.sound.StopSoundService;

public class AppNotifications
{
    public static void clearAll(Context context)
    {
        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Cancel all notifications
        notificationManager.cancel(context.getString(R.string.appName).hashCode());

        // Stop alert playing
        StopSoundService.stopSoundService(context);
    }
}
