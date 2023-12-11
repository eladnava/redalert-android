package com.red.alert.logic.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;

import android.media.AudioAttributes;
import android.os.Build;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.activities.AlertPopup;
import com.red.alert.activities.Main;
import com.red.alert.config.Logging;
import com.red.alert.config.NotificationChannels;
import com.red.alert.config.Sound;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.intents.AlertPopupParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.communication.intents.RocketNotificationParameters;
import com.red.alert.logic.feedback.VibrationLogic;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.logic.phone.PowerManagement;
import com.red.alert.receivers.NotificationDeletedReceiver;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.DateTime;

public class RocketNotifications {
    public static void notify(Context context, String city, String notificationTitle, String notificationContent, String alertType, String threatType, String overrideSound) {
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
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(context.getResources().getColor(R.color.colorAccent))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationContent))
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher));

        // Handle notification delete
        builder.setDeleteIntent(getNotificationDeletedReceiverIntent(context));

        // No click event for test notifications
        if (!alertType.contains("test")) {
            // Handle notification click
            builder.setContentIntent(getNotificationIntent(city, threatType, context));
        }

        // Generate a notification ID based on the unique hash-code of the alert zone
        int notificationId = notificationTitle.hashCode();

        // Cancel previous notification for same alert city
        notificationManager.cancel(notificationId);

        // Configure notification channel (if required)
        setNotificationChannel(alertType, overrideSound, builder, context);

        try {
            // Issue the notification
            notificationManager.notify(notificationId, builder.build());
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Rocket notification failed", exc);
        }

        // Vibrate (if applicable)
        VibrationLogic.issueVibration(alertType, context);

        // Play alert sound (if applicable)
        SoundLogic.playSound(alertType, overrideSound, context);

        // Wake up phone screen (if enabled)
        PowerManagement.wakeUpScreen(alertType, city, context);

        // Show alert popup (if applicable)
        AlertPopup.showAlertPopup(alertType, city, threatType, context);

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

    public static PendingIntent getNotificationIntent(String city, String threatType, Context context) {
        // Prepare notification intent
        Intent notificationIntent = new Intent(context, Main.class);

        // Pass on city name, threat type, and alert received timestamp
        notificationIntent.putExtra(AlertPopupParameters.CITY, city);
        notificationIntent.putExtra(AlertPopupParameters.THREAT_TYPE, threatType);
        notificationIntent.putExtra(AlertPopupParameters.TIMESTAMP, DateTime.getUnixTimestamp());

        // Prepare pending intent
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Return it
        return pendingIntent;
    }

    public static String getNotificationChannelId(String alertType, String overrideSound, Context context) {
        // Initialize notification channel params
        String channelId = NotificationChannels.PRIMARY_ALERT_NOTIFICATION_CHANNEL_ID;

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND)) {
            channelId = NotificationChannels.SECONDARY_ALERT_NOTIFICATION_CHANNEL_ID;
        }

        // Using custom sound?
        if (SoundLogic.getAlertSoundName(alertType, overrideSound, context).equals(Sound.CUSTOM_SOUND_NAME)) {
            channelId += NotificationChannels.CUSTOM_SOUND_NOTIFICATION_CHANNEL_SUFFIX;
        }

        // All done
        return channelId;
    }

    public static void setNotificationChannel(String alertType, String overrideSound, NotificationCompat.Builder builder, Context context) {
        // Android O and up
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        // Determine notification channel ID
        String channelId = getNotificationChannelId(alertType, overrideSound, context);

        // Get notification channel name
        String channelName = NotificationChannels.PRIMARY_ALERT_NOTIFICATION_CHANNEL_NAME;

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND)) {
            channelName = NotificationChannels.SECONDARY_ALERT_NOTIFICATION_CHANNEL_NAME;
        }

        // Get notification manager instance
        NotificationManager manager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Initialize channel (set high importance)
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);

        // Set dummy audio attributes (sound will be silent)
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

        // Set silent sound (play sound manually using MediaPlayer APIs to override silent mode)
        channel.setSound(SoundLogic.getAppSoundByResourceName("silent", context), audioAttributes);

        // Disable vibration (vibrate manually to override silent mode)
        channel.enableVibration(false);

        // Bypass do-not-disturb mode
        channel.setBypassDnd(true);

        // Enable lights
        channel.enableLights(true);

        // Display flashing red color on supported phones
        channel.setLightColor(Color.RED);

        // Create channel (does nothing if already exists)
        manager.createNotificationChannel(channel);

        // Configure builder to use channel ID
        if (builder != null) {
            builder.setChannelId(channelId);
        }
    }
}
