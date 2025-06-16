package com.red.alert.logic.alerts;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.Alerts;
import com.red.alert.config.Logging;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.notifications.Notifications;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlertLogic {
    public static void processIncomingAlert(String threatType, String citiesPSVString, String alertType, String alertId, String instructions, String leaveShelter, Context context) {
        // Override threat type in case leaveShelter === "1" (sent as earlyWarning for backwards-compatibility)
        if (leaveShelter != null && leaveShelter.equals("1")) {
            threatType = ThreatTypes.LEAVE_SHELTER;
        }

        // No cities?
        if (StringUtils.stringIsNullOrEmpty(citiesPSVString)) {
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
        Log.i(Logging.TAG, "Received alert (" + threatType + "): " + citiesPSVString);

        // Early warning?
        if (threatType.equals(ThreatTypes.EARLY_WARNING)) {
            // Disabled by user?
            if (!AppPreferences.getEarlyWarningNotificationsEnabled(context)) {
                Log.i(Logging.TAG, "User disabled early warnings, ignoring alert");
                return;
            }
        }

        // Leave shelter alert?
        if (threatType.equals(ThreatTypes.LEAVE_SHELTER)) {
            // Disabled by user?
            if (!AppPreferences.getLeaveShelterNotificationsEnabled(context)) {
                Log.i(Logging.TAG, "User disabled leave shelter alerts, ignoring alert");
                return;
            }
        }

        // Get alert cities as list
        List<String> cityList = LocationData.explodePSV(citiesPSVString);

        // Prepare list of relevant cities to alert about
        List<String> relevantCities = new ArrayList<>();

        // Loop over cities
        for (String city : cityList) {
            // Is this a relevant city?
            if (isRelevantCity(alertType, city, context)) {
                // Not a test alert?
                if (!alertType.contains(AlertTypes.TEST)) {
                    // Did we recently notify for this city?
                    if (cityRecentlyNotified(city, threatType, context)) {
                        // Log that we're skipping this one
                        Log.i(Logging.TAG, "Ignoring recently notified city: " + city);
                        continue;
                    }

                    // Did we already notify for this city and alert ID combination?
                    if (cityAlreadyNotifiedForAlertId(city, alertId, context)) {
                        // Log that we're skipping this one
                        Log.i(Logging.TAG, "Ignoring already notified alert ID " + alertId + " for city: " + city);
                        continue;
                    }

                    // Not an early warning?
                    // Save city last alert timestamp to prevent duplicate alerts
                    if (!threatType.equals(ThreatTypes.EARLY_WARNING) && !threatType.equals(ThreatTypes.LEAVE_SHELTER)) {
                        AppPreferences.setCityLastAlertTime(city, DateTime.getUnixTimestamp(), context);
                    }
                }

                // Add city to relevant alert cities
                relevantCities.add(city);
            }
        }

        // Any cities to alert about?
        if (relevantCities.size() > 0) {
            // Issue the notification
            Notifications.notify(context, relevantCities, alertType, threatType, null, instructions);
        }
    }

    static boolean cityRecentlyNotified(String city, String threatType, Context context) {
        // Unlimited early warnings / leave shelter alerts
        if (threatType.equals(ThreatTypes.EARLY_WARNING) || threatType.equals(ThreatTypes.LEAVE_SHELTER)) {
            return false;
        }

        // Buffer time in between alerts for same city (to prevent duplicate alerts)
        long recentCutoffTimestamp = DateTime.getUnixTimestamp() - Alerts.DUPLICATE_ALERTS_PADDING_TIME;

        // Check that enough time passed
        return AppPreferences.getCityLastAlert(city, context) > recentCutoffTimestamp;
    }

    static boolean cityAlreadyNotifiedForAlertId(String city, String alertId, Context context) {
        // No alert ID?
        if (StringUtils.stringIsNullOrEmpty(alertId)) {
            return false;
        }

        // Build SharedPreference key comprising of city name (in Hebrew) and alert ID
        String key = city + "-" + alertId;

        // Check for a SharedPreference key with this city name and alert ID
        if (Singleton.getSharedPreferences(context).getBoolean(key, false)) {
            return true;
        }

        // Store as already processed
        Singleton.getSharedPreferences(context).edit().putBoolean(key, true).commit();

        // First time encountering this city & alert ID combo
        return false;
    }

    public static boolean isRelevantCity(String alertType, String city, Context context) {
        // Test or system message?
        if (isSystemTestAlert(alertType)) {
            return true;
        }

        // Did user select this city? Either via entire zones or cities
        if (isCitySelectedPrimarily(city, false, context)) {
            return true;
        }

        // Did user select this city?
        if (isSecondaryCitySelected(city, false, context)) {
            return true;
        }

        // Are we nearby?
        if (isNearby(city, context)) {
            return true;
        }

        // Irrelevant
        return false;
    }

    public static boolean isSecondaryAlert(String alertType, List<String> cities, Context context) {
        // System or test alert?
        if (isSystemTestAlert(alertType)) {
            return false;
        }

        // By default, no secondary until proven otherwise
        boolean secondaryFound = false;

        // Traverse cities
        for (String city : cities) {
            // Did user select this area under primary city / zone selection?
            if (isCitySelectedPrimarily(city, false, context)) {
                return false;
            }

            // Location alerts enabled and city is nearby?
            // It's a primary alert then, not a secondary alert
            if (isNearby(city, context)) {
                return false;
            }

            // Did user select this city under secondary city selection?
            if (isSecondaryCitySelected(city, false, context)) {
                secondaryFound = true;
            }
        }

        // If we are still here, return secondaryFound flag
        return secondaryFound;
    }

    public static boolean isCitySelectedPrimarily(String city, boolean ignoreAllSelection, Context context) {
        // Get enabled / disabled setting
        boolean notificationsEnabled = AppPreferences.getNotificationsEnabled(context);

        // Are real time alerts disabled?
        if (!notificationsEnabled) {
            return false;
        }

        // Alert applies for all cities?
        if (city.equals("ברחבי הארץ")) {
            return true;
        }

        // Get user's selected zones
        String selectedZones = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedZonesPref), context.getString(R.string.none));

        // All are selected?
        if (!ignoreAllSelection && (StringUtils.stringIsNullOrEmpty(selectedZones) || selectedZones.equals(context.getString(R.string.all)))) {
            return true;
        }

        // Explode selected zones into array
        List<String> selectedZonesList = Arrays.asList(selectedZones.split("\\|"));

        // Get zone by city name
        String cityZone = LocationData.getZoneByCityName(city, context);

        // Check for containment
        if (selectedZonesList.contains(cityZone)) {
            return true;
        }

        // Get user's selected cities
        String selectedCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedCitiesPref), context.getString(R.string.none));

        // All selected?
        if (StringUtils.stringIsNullOrEmpty(selectedCities) || selectedCities.equals(context.getString(R.string.all))) {
            return true;
        }

        // Explode into array
        List<String> selectedCitiesList = LocationData.explodePSV(selectedCities);

        // Selected this city?
        if (selectedCitiesList.contains(city)) {
            // We got a match!
            return true;
        }

        // No match
        return false;
    }

    public static boolean isSecondaryCitySelected(String city, boolean ignoreAllSelection, Context context) {
        // Get main enabled setting
        boolean notificationsEnabled = AppPreferences.getNotificationsEnabled(context);

        // Are real time alerts enabled?
        if (!notificationsEnabled) {
            return false;
        }

        // Get secondary enabled setting
        notificationsEnabled = AppPreferences.getSecondaryNotificationsEnabled(context);

        // Are secondary alerts enabled?
        if (!notificationsEnabled) {
            return false;
        }

        // Get user's secondary cities
        String secondaryCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedSecondaryCitiesPref), context.getString(R.string.none));

        // All selected?
        if (!ignoreAllSelection && (StringUtils.stringIsNullOrEmpty(secondaryCities) || secondaryCities.equals(context.getString(R.string.all)))) {
            return true;
        }

        // Get selected cities as list
        List<String> selectedCities = LocationData.explodePSV(secondaryCities);

        // Did we select this city?
        if (selectedCities.contains(city)) {
            return true;
        }

        // No match
        return false;
    }

    public static boolean isNearby(String cityName, Context context) {
        // Are location alerts enabled?
        if (!AppPreferences.getLocationAlertsEnabled(context)) {
            return false;
        }

        // Get current location (last known)
        Location myLocation = LocationLogic.getCurrentLocation(context);

        // No recent location?
        if (myLocation == null) {
            return false;
        }

        // Get city geolocation
        Location cityLocation = LocationData.getCityLocation(cityName, context);

        // No city found?
        if (cityLocation == null) {
            return false;
        }

        // Get max distance configured for location-based alerts
        double maxDistance = LocationLogic.getMaxDistanceKilometers(context, -1);

        // Calculate distance to city in kilometers
        float distance = cityLocation.distanceTo(myLocation) / 1000;

        // Distance is less than max?
        if (distance <= maxDistance) {
            // We are nearby!
            return true;
        }

        // We are too far away from this city
        return false;
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
