package com.red.alert.config;

public class Alerts {
    // Date format for recent alerts display
    public static final String DATE_FORMAT = "HH:mm:ss";

    // Time that must pass (in seconds) in between alerts for the same zone (to avoid duplicate alerts)
    public static final int DUPLICATE_ALERTS_PADDING_TIME = 60 * 2;

}
