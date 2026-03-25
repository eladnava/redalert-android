package com.red.alert.config;

public class Alerts {
    // Date format for recent alerts display
    public static final String DATE_FORMAT = "HH:mm:ss";

    // Time to wait before calling finish() on AlertPopup after POST_IMPACT_WAIT_MINUTES passed
    public static final int ALERT_POPUP_DONE_PADDING = 10;

    // Time that must pass (in seconds) in between alerts for the same zone (to avoid duplicate alerts)
    public static final int DUPLICATE_ALERTS_PADDING_TIME = 60 * 2;

    // Max time after a go-to-shelter alert that the app should play a sound for Leave Shelter alerts (to avoid waking up sleeping civilians)
    public static final int LEAVE_SHELTER_ALERTS_SOUND_MAX_CUTOFF_TIME = 60 * 30;

}
