package com.red.alert.utils.networking;

import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;

import com.red.alert.R;
import com.red.alert.config.Notifications;
import com.red.alert.logic.notifications.RocketNotifications;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.utils.localization.Localization;

public class Connectivity {
    public static boolean isConnected(Context context) {
        // Grab connectivity service
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get current network (may be null)
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();

        // Check connectivity: got an active network and it is connected?
        return activeNetInfo != null && activeNetInfo.isConnected();
    }

    public static void refreshConnectionNotification(final Context context) {
        // Get notification manager
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Disabled the disconnection notification?
        if (!AppPreferences.getDisconnectedNotificationEnabled(context)) {
            // Clear old notification
            notificationManager.cancel(Notifications.CONNECTIVITY_NOTIFICATION_ID);
            return;
        }

        // Override locale for language-friendly notification
        // (it sometimes gets reset when the service is running for a long time)
        Localization.overridePhoneLocale(context);

        // By default, connecting
        int notificationIcon = R.drawable.ic_warning;

        // Default text
        String notificationText = context.getString(R.string.notConnected);

        // Create a new notification and style it
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.appName))
                .setContentText(notificationText)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSmallIcon(notificationIcon);

        // Handle notification click
        builder.setContentIntent(RocketNotifications.getNotificationIntent(context));

        // Not connected and requested to be notified?
        if (!isConnected(context)) {
            notificationManager.notify(Notifications.CONNECTIVITY_NOTIFICATION_ID, builder.build());
        }
        else {
            // We're connected
            //
            // Wait a few seconds for push services to reconnect
            // In the future, wait for their connectivity broadcast
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Still connected?
                    if (isConnected(context)) {
                        // Clear old notification
                        notificationManager.cancel(Notifications.CONNECTIVITY_NOTIFICATION_ID);
                    }
                }
            }, Notifications.CONNECTIVITY_NOTIFICATION_HIDE_TIMEOUT);
        }
    }
}
