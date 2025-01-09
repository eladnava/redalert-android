package com.github.timboode.NYP_alert_android.config;

import android.media.AudioManager;

public class Sound {
    public static final String SCHEME_URI_IDENTIFIER = "://";
    public static final String APP_SOUND_PREFIX = "com.github.timboode.NYP_alert_android://";

    // The AudioManager stream that sirens will be played on
    public static int STREAM_TYPE = AudioManager.STREAM_ALARM;

    // DUser selected custom sound key
    public static String CUSTOM_SOUND_NAME = "custom";
}
