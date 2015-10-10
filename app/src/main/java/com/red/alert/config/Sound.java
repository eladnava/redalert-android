package com.red.alert.config;

import android.media.AudioManager;

public class Sound
{
    // The AudioManager stream that sirens will be played on
    public static int STREAM_TYPE = AudioManager.STREAM_ALARM;

    public static final String SCHEME_URI_IDENTIFIER = "://";
    public static final String APP_SOUND_PREFIX = "com.red.alert://";
}
