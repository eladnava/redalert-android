package com.red.alert.utils.feedback;

import android.app.Activity;

import com.red.alert.config.Sound;

public class Volume
{
    public static void setVolumeKeysAction(Activity context)
    {
        // Set the appropriate alert stream
        context.setVolumeControlStream(Sound.STREAM_TYPE);
    }
}
