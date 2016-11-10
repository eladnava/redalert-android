package com.red.alert.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.red.alert.logic.communication.intents.RocketNotificationParameters;
import com.red.alert.services.sound.StopSoundService;

public class NotificationDeletedReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Got deleted notification event?
        if (RocketNotificationParameters.NOTIFICATION_DELETED_ACTION.equals(intent.getAction())) {
            // Silence sound via service
            context.startService(new Intent(context, StopSoundService.class));
        }
    }
}
