package com.github.timboode.NYP_alert_android.config;

public class NotificationChannels {
    // Primary alerts notification channel config
    public static final String PRIMARY_ALERT_NOTIFICATION_CHANNEL_ID = "redalert";
    public static final String PRIMARY_ALERT_NOTIFICATION_CHANNEL_NAME = "Alerts";

    // Secondary alerts notification channel config
    public static final String SECONDARY_ALERT_NOTIFICATION_CHANNEL_ID = "redalert_secondary";
    public static final String SECONDARY_ALERT_NOTIFICATION_CHANNEL_NAME = "Secondary Alerts";

    // Custom sound notification channel suffix
    public static final String CUSTOM_SOUND_NOTIFICATION_CHANNEL_SUFFIX = "_custom";

    // Location service foreground notification channel
    public static final String LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_ID = "redalert_location_v2";
    public static final String LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_OLD_ID = "redalert_location";
    public static final String LOCATION_SERVICE_FOREGROUND_NOTIFICATION_CHANNEL_NAME = "Location Service";

    // Old channels that should be deleted due to resource ID bug
    public static final String OLD_PRIMARY_ALERT_NOTIFICATION_CHANNEL_ID = "redalert";
    public static final String OLD_SECONDARY_ALERT_NOTIFICATION_CHANNEL_ID = "redalert_secondary";

}
