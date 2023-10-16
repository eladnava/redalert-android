package com.red.alert.logic.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import me.pushy.sdk.Pushy;

import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.activities.AlertPopup;
import com.red.alert.activities.Main;
import com.red.alert.config.Logging;
import com.red.alert.config.Sound;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.communication.intents.RocketNotificationParameters;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.receivers.NotificationDeletedReceiver;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;

public class RocketNotifications {
    // Silent notification channel config for no-sound alerts
    public static final String SILENT_NOTIFICATION_CHANNEL_ID = "redalert_silent";
    public static final String SILENT_NOTIFICATION_CHANNEL_NAME = "Silent Notifications";

    public static void notify(Context context, String city, String notificationTitle, String notificationContent, String alertType, String overrideSound) {
        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // In case there is no content
        if (StringUtils.stringIsNullOrEmpty(notificationContent)) {
            // Move title to content
            notificationContent = notificationTitle;

            // Set title as app name
            notificationTitle = context.getString(R.string.appName);
        }

        // Create a new notification and style it
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setTicker(notificationContent)
                .setAutoCancel(true)
                .setContentTitle(notificationTitle)
                .setContentText(notificationContent)
                .setLights(Color.RED, 1000, 1000)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(Color.parseColor("#ff4032"))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationContent))
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher));

        // Handle notification delete
        builder.setDeleteIntent(getNotificationDeletedReceiverIntent(context));

        // No click event for test notifications
        if (!alertType.contains("test")) {
            // Handle notification click
            builder.setContentIntent(getNotificationIntent(context));
        }

        // Generate a notification ID based on the unique hash-code of the alert zone
        int notificationID = notificationTitle.hashCode();

        // Cancel previous notification for same alert zone
        notificationManager.cancel(notificationID);

        // Automatically configure notification channel (if required)
        setNotificationChannel(alertType, builder, context);

        try {
            // Issue the notification
            notificationManager.notify(notificationID, builder.build());
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Rocket notification failed", exc);
        }

        // Play alert sound (if applicable)
        SoundLogic.playSound(alertType, overrideSound, context);

        // Show alert popup (if applicable)
        AlertPopup.showAlertPopup(alertType, city, context);

        // Reload recent alerts (if main activity is open)
        Broadcasts.publish(context, MainActivityParameters.RELOAD_RECENT_ALERTS);
    }

    private static PendingIntent getNotificationDeletedReceiverIntent(Context context) {
        // Prepare delete intent
        Intent deleteIntent = new Intent(context, NotificationDeletedReceiver.class);

        // Set action
        deleteIntent.setAction(RocketNotificationParameters.NOTIFICATION_DELETED_ACTION);

        // Get broadcast receiver
        return PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent getNotificationIntent(Context context) {
        // Prepare notification intent
        Intent notificationIntent = new Intent(context, Main.class);

        // Prepare pending intent
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Return it
        return pendingIntent;
    }

    private static void setNotificationChannel(String alertType, NotificationCompat.Builder builder, Context context) {
        // Android O and up (no channels before then)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        // Get path to alert sound resource for current alert type
        Uri alarmSoundURI = SoundLogic.getAlertSound(alertType, null, context);

        // "Silent" sound selected for this alert type?
        if (alarmSoundURI == null) {
            // Use silent alert notification channel
            setSilentNotificationChannel(builder, context);
        }
        else {
            // Use standard high-importance channel with sound + vibrate
            Pushy.setNotificationChannel(builder, context);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void setSilentNotificationChannel(NotificationCompat.Builder builder, Context context) {
        // Get notification manager instance
        NotificationManager manager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Initialize channel (low importance so Android does not force sound and vibration)
        NotificationChannel channel = new NotificationChannel(SILENT_NOTIFICATION_CHANNEL_ID, SILENT_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);

        // Create channel (does nothing if already exists)
        manager.createNotificationChannel(channel);

        // Set notification channel on builder
        builder.setChannelId(SILENT_NOTIFICATION_CHANNEL_ID);
    }
}
