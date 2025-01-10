package com.github.timboode.NYP_alert_android.logic.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class IncomingMessageService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // TODO
    }
}
