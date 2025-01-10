package com.github.timboode.NYP_alert_android.logic.alerts;

import android.content.Context;
import android.util.Log;

import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.config.ThreatTypes;
import com.github.timboode.NYP_alert_android.logic.notifications.Notifications;
import com.github.timboode.NYP_alert_android.utils.formatting.StringUtils;
import com.github.timboode.NYP_alert_android.utils.localization.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlertLogic {
    public static void processIncomingAlert(String threatType, String alertMessage, String alertTitle, Context context) {
        // No cities?
        if (StringUtils.stringIsNullOrEmpty(alertMessage)) {
            return;
        }

        // No threat specified?
        if (StringUtils.stringIsNullOrEmpty(threatType)) {
            // Default to system alert threat type
            threatType = ThreatTypes.SYSTEM;
        }

        // Ensure the right language is displayed
        Localization.overridePhoneLocale(context);

        // Log the cities
        Log.i(Logging.TAG, "Received alert (" + threatType + "): ");

        // Issue the notification
        Notifications.notify(context, alertTitle, alertMessage, threatType, null);
    }

    public static boolean isSecondaryAlert(String alertType, Context context) {
        // System or test alert?
        if (isSystemTestAlert(alertType)) {
            return false;
        }

        // By default, no secondary until proven otherwise
        boolean secondaryFound = false; // TODO determine if alert is secondary

        // If we are still here, return secondaryFound flag
        return secondaryFound;
    }

    public static boolean isSystemTestAlert(String alertType) {
        // Self-test (or sound test)?
        if (alertType.contains(AlertTypes.TEST)) {
            return true;
        }

        // System message?
        if (alertType.equals(AlertTypes.SYSTEM)) {
            return true;
        }

        // Not system nor test
        return false;
    }
}
