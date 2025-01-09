package com.github.timboode.NYP_alert_android.utils.feedback;

import android.app.Activity;

import com.github.timboode.NYP_alert_android.config.Sound;

public class Volume {
    public static void setVolumeKeysAction(Activity context) {
        // Set the appropriate alert stream
        context.setVolumeControlStream(Sound.STREAM_TYPE);
    }
}
