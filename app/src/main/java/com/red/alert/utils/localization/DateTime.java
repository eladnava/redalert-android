package com.red.alert.utils.localization;

public class DateTime {
    public static final long getUnixTimestamp() {
        return (System.currentTimeMillis() / 1000);
    }
}
