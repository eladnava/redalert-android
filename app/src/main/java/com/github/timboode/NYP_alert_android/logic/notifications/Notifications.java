package com.github.timboode.NYP_alert_android.logic.notifications;

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
import android.text.TextUtils;
import android.util.Log;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.activities.Popup;
import com.github.timboode.NYP_alert_android.activities.Main;
import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.config.NotificationChannels;
import com.github.timboode.NYP_alert_android.config.Sound;
import com.github.timboode.NYP_alert_android.config.ThreatTypes;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertLogic;
import com.github.timboode.NYP_alert_android.logic.alerts.AlertTypes;
import com.github.timboode.NYP_alert_android.logic.communication.intents.AlertPopupParameters;
import com.github.timboode.NYP_alert_android.logic.communication.intents.MainActivityParameters;
import com.github.timboode.NYP_alert_android.logic.communication.intents.RocketNotificationParameters;
import com.github.timboode.NYP_alert_android.logic.feedback.VibrationLogic;
import com.github.timboode.NYP_alert_android.logic.feedback.sound.SoundLogic;
import com.github.timboode.NYP_alert_android.logic.phone.PowerManagement;
import com.github.timboode.NYP_alert_android.receivers.NotificationDeletedReceiver;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;
import com.github.timboode.NYP_alert_android.utils.formatting.StringUtils;
import com.github.timboode.NYP_alert_android.utils.localization.DateTime;

import java.util.List;

public class Notifications {
    public static void notify(Context context, String title, String message, String threatType, String overrideSound) {
        if (!threatType.contains(ThreatTypes.TEST)) {
            // For all other threat types, only display threat instructions in notification body (don't display zone / countdown)
            message = "TEST -> " + message;
        }

        // In case there is no content
        if (StringUtils.stringIsNullOrEmpty(message)) {
            // Move title to content
            message = title;

            // Set title as app name
            title = context.getString(R.string.appName);
        }

        // Sound picker open?
        if (threatType.contains(AlertTypes.TEST_SOUND)) {
            // Set special notification description for sound testing notifications
            message = context.getString(R.string.testSound);
        }

        // System message?
        if (threatType.equals(AlertTypes.SYSTEM)) {
            // Set title to app name
            title = context.getString(R.string.appName);
        }

        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Create a new notification and style it
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setTicker(message)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message)
                .setLights(Color.RED, 1000, 1000)
                .setSmallIcon(R.drawable.ic_notify)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(context.getResources().getColor(R.color.colorAccent))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        // Only display large icon in case title is less than X characters long
        // as it causes the title to get truncated prematurely
        if (title.length() < 55 || !threatType.contains(ThreatTypes.MISSILES)) {
            builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), ThreatTypeDrawableMapper.ThreatTypeToDrawable(threatType)));
        }

        // Handle notification delete
        builder.setDeleteIntent(getNotificationDeletedReceiverIntent(context));

        // No click event for test notifications
        if (!threatType.contains(AlertTypes.TEST)) {
            // Handle notification click
            builder.setContentIntent(getNotificationIntent(threatType, context));
        }

        // Generate a notification ID based on the unique hash-code of the notification title string to avoid duplicates
        int notificationId = message.hashCode();

        // Cancel previous notification with same exact notification title string
        notificationManager.cancel(notificationId);

        // Configure notification channel (if required)
        setNotificationChannel(threatType, overrideSound, builder, context);

        try {
            // Issue the notification
            notificationManager.notify(notificationId, builder.build());
        }
        catch (Exception exc) {
            // Log error
            Log.e(Logging.TAG, "Failed to create and display notification", exc);
        }

        // Vibrate (if applicable)
        VibrationLogic.issueVibration(threatType, context);

        // Play alert sound (if applicable)
        SoundLogic.playSound(threatType, overrideSound, context);

        // Wake up phone screen (if enabled)
        PowerManagement.wakeUpScreen(threatType, context);

        // Show alert popup (if applicable)
        Popup.showAlertPopup(threatType, message, threatType, context);

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

    public static PendingIntent getNotificationIntent(String threatType, Context context) {
        // Prepare notification intent
        Intent notificationIntent = new Intent(context, Main.class);

        // Pass on city name, threat type, and alert received timestamp
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
        else {
            // No custom sound
            // Add version suffix to work around sound resource bug
            channelId += "_v2";
        }

        // All done
        return channelId;
    }

    public static void setNotificationChannel(String alertType, String overrideSound, NotificationCompat.Builder builder, Context context) {
        // Android O and up
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        // Get notification manager instance
        NotificationManager manager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Delete old (bugged) channels because notification channels persist sound resource IDs which change between builds
        manager.deleteNotificationChannel(NotificationChannels.OLD_PRIMARY_ALERT_NOTIFICATION_CHANNEL_ID);
        manager.deleteNotificationChannel(NotificationChannels.OLD_SECONDARY_ALERT_NOTIFICATION_CHANNEL_ID);

        // Determine notification channel ID
        String channelId = getNotificationChannelId(alertType, overrideSound, context);

        // Get notification channel name
        String channelName = NotificationChannels.PRIMARY_ALERT_NOTIFICATION_CHANNEL_NAME;

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND)) {
            channelName = NotificationChannels.SECONDARY_ALERT_NOTIFICATION_CHANNEL_NAME;
        }

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
