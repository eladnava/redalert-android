package com.red.alert.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.red.alert.logic.communication.intents.RocketNotificationParameters;
import com.red.alert.logic.feedback.sound.SoundLogic;

public class NotificationDeletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Got deleted notification event?
        if (RocketNotificationParameters.NOTIFICATION_DELETED_ACTION.equals(intent.getAction())) {
            // Silence currently playing sound
            SoundLogic.stopSound(context);
        }
    }
}
