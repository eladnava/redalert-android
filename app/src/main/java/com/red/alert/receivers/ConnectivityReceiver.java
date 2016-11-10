package com.red.alert.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.red.alert.utils.networking.Connectivity;

public class ConnectivityReceiver extends WakefulBroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // Refresh the disconnection notification
        Connectivity.refreshConnectionNotification(context);
    }
}
