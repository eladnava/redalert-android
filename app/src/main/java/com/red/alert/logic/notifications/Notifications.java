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
import android.text.TextUtils;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.activities.AlertPopup;
import com.red.alert.activities.Main;
import com.red.alert.config.Logging;
import com.red.alert.config.NotificationChannels;
import com.red.alert.config.Sound;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.alerts.AlertLogic;
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
import com.red.alert.utils.metadata.LocationData;

import java.util.List;

public class Notifications {
    public static void notify(Context context, List<String> cities, String alertType, String threatType, String overrideSound) {
        // Localize threat type
        String localizedThreatType = LocationData.getLocalizedThreatType(threatType, context);

        // Prepare notification title with threat type and city name
        String notificationTitle = localizedThreatType + ": " + LocationData.getLocalizedCityNamesCSV(cities, context);

        // Prepare notification body with zone and countdown
        String notificationContent = LocationData.getLocalizedCityZonesWithCountdownCSV(cities, context);

        // Missile alert?
        if (threatType.contains(ThreatTypes.MISSILES)) {
            // Add line break if needed
            if (!StringUtils.stringIsNullOrEmpty(notificationContent)) {
                notificationContent += "\n";
            }

            // Add threat instructions to notification body in a new line after zone and countdown
            notificationContent += LocationData.getLocalizedThreatInstructions(threatType, context);
        } else if (!threatType.contains(ThreatTypes.TEST)) {
            // For all other threat types, only display threat instructions in notification body (don't display zone / countdown)
            notificationContent = LocationData.getLocalizedThreatInstructions(threatType, context);
        }

        // In case there is no content
        if (StringUtils.stringIsNullOrEmpty(notificationContent)) {
            // Move title to content
            notificationContent = notificationTitle;

            // Set title as app name
            notificationTitle = context.getString(R.string.appName);
        }

        // Sound picker open?
        if (alertType.contains(AlertTypes.TEST_SOUND)) {
            // Set special notification description for sound testing notifications
            notificationContent = context.getString(R.string.testSound);
        }

        // System message?
        if (alertType.equals(AlertTypes.SYSTEM)) {
            // Set title to app name
            notificationTitle = context.getString(R.string.appName);

            // Set notification body to system message text contained in cities variable
            notificationContent = TextUtils.join(", ", cities);
        }

        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

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
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationContent));

        // Only display large icon in case title is less than X characters long
        // as it causes the title to get truncated prematurely
        if (notificationTitle.length() < 55) {
            builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), LocationData.getThreatDrawable(threatType)));
        }

        // Handle notification delete
        builder.setDeleteIntent(getNotificationDeletedReceiverIntent(context));

        // No click event for test notifications
        if (!alertType.contains(AlertTypes.TEST)) {
            // Handle notification click
            builder.setContentIntent(getNotificationIntent(cities, threatType, context));
        }

        // Generate a notification ID based on the unique hash-code of the notification title string to avoid duplicates
        int notificationId = notificationTitle.hashCode();

        // Cancel previous notification with same exact notification title string
        notificationManager.cancel(notificationId);

        // Secondary alert?
        if (AlertLogic.isSecondaryAlert(alertType, cities, context)) {
            // Override type
            alertType = AlertTypes.SECONDARY;
        }

        // Configure notification channel (if required)
        setNotificationChannel(alertType, overrideSound, builder, context);

        try {
            // Issue the notification
            notificationManager.notify(notificationId, builder.build());
        }
        catch (Exception exc) {
            // Log error
            Log.e(Logging.TAG, "Failed to create and display notification", exc);
        }

        // Vibrate (if applicable)
        VibrationLogic.issueVibration(alertType, context);

        // Play alert sound (if applicable)
        SoundLogic.playSound(alertType, overrideSound, context);

        // Wake up phone screen (if enabled)
        PowerManagement.wakeUpScreen(alertType, context);

        // Show alert popup (if applicable)
        AlertPopup.showAlertPopup(alertType, cities, threatType, context);

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

    public static PendingIntent getNotificationIntent(List<String> cities, String threatType, Context context) {
        // Prepare notification intent
        Intent notificationIntent = new Intent(context, Main.class);

        // Pass on city name, threat type, and alert received timestamp
        notificationIntent.putExtra(AlertPopupParameters.THREAT_TYPE, threatType);
        notificationIntent.putExtra(AlertPopupParameters.CITIES, cities.toArray(new String[0]));
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
