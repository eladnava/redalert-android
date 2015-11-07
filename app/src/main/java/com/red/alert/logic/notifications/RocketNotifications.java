package com.red.alert.logic.notifications;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.activities.AlertPopup;
import com.red.alert.activities.Main;
import com.red.alert.config.Logging;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.communication.intents.RocketNotificationParameters;
import com.red.alert.logic.communication.intents.SoundServiceParameters;
import com.red.alert.logic.integration.BluetoothIntegration;
import com.red.alert.services.sound.PlaySoundService;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;

public class RocketNotifications
{
    public static void notify(Context context, String alertZone, String notificationTitle, String notificationContent, String alertType, String overrideSound)
    {
        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // In case there is no content
        if (StringUtils.stringIsNullOrEmpty(notificationContent))
        {
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
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationContent))
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher));

        // Handle notification delete
        builder.setDeleteIntent(getNotificationDeletedReceiverIntent(context));

        // Handle notification click
        builder.setContentIntent(getNotificationIntent(context));

        // Generate a notification ID based on the unique hash-code of the alert zone
        int notificationID = notificationTitle.hashCode();

        // Cancel previous notification for same alert zone
        notificationManager.cancel(notificationID);

        try
        {
            // Issue the notification
            notificationManager.notify(notificationID, builder.build());
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Rocket notification failed", exc);

            // Show it as a toast message
            Toast.makeText(context, notificationTitle + " - " + notificationContent, Toast.LENGTH_LONG).show();
        }

        // Play alert sound (if applicable)
        playAlertSound(alertType, overrideSound, context);

        // Show alert popup (if applicable)
        AlertPopup.showAlertPopup(alertType, alertZone, context);

        // Notify BLE devices (if applicable)
        BluetoothIntegration.notifyDevices(alertType, context);

        // Reload recent alerts (if main activity is open)
        Broadcasts.publish(context, MainActivityParameters.RELOAD_RECENT_ALERTS);
    }

    private static PendingIntent getNotificationDeletedReceiverIntent(Context context)
    {
        // Prepare delete intent
        Intent deleteIntent = new Intent(RocketNotificationParameters.NOTIFICATION_DELETED_ACTION);

        // Get broadcast receiver
        return PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
    }

    public static PendingIntent getNotificationIntent(Context context)
    {
        // Prepare notification intent
        Intent notificationIntent = new Intent(context, Main.class);

        // Prepare pending intent
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Return it
        return pendingIntent;
    }

    static void playAlertSound(String alertType, String sound, Context context)
    {
        // Create a new intent to start our sound service
        Intent playSound = new Intent(context, PlaySoundService.class);

        // Set the type
        playSound.putExtra(SoundServiceParameters.ALERT_TYPE, alertType);

        // Got a sound?
        if (sound != null)
        {
            playSound.putExtra(SoundServiceParameters.ALERT_SOUND, sound);
        }

        // Start the service
        context.startService(playSound);
    }
}
