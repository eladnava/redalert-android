package com.red.alert.logic.alerts;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.Alerts;
import com.red.alert.config.Logging;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.notifications.RocketNotifications;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.metadata.LocationData;

import java.util.Arrays;
import java.util.List;

public class AlertLogic {
    public static void processIncomingAlert(String zoneCSVString, String alertType, Context context) {
        // No zones?
        if (StringUtils.stringIsNullOrEmpty(zoneCSVString)) {
            return;
        }

        // Log the zones
        Log.i(Logging.TAG, "Received alert (" + alertType + "): " + zoneCSVString);

        // Get zones as list
        List<String> zoneList = LocationData.explodeZonesCSV(zoneCSVString);

        // Loop over zones
        for (String zone : zoneList) {
            // Store default alert type in variable, override it later
            String overrideAlertType = alertType;

            // Is this a relevant zone?
            if (isRelevantZone(overrideAlertType, zone, context)) {
                // Secondary alert?
                if (isSecondaryZone(overrideAlertType, zone, context)) {
                    // Set type
                    overrideAlertType = AlertTypes.SECONDARY;
                }

                // Not a test alert?
                if (!overrideAlertType.contains("test")) {
                    // Did we recently notify for this zone?
                    if (zoneRecentlyNotified(zone, context)) {
                        continue;
                    }

                    // Save zone last alert timestamp to prevent duplicate alerts
                    AppPreferences.setZoneLastAlertTime(zone, DateTime.getUnixTimestamp(), context);
                }

                // Get area names
                String cityNames = LocationData.getCityNamesByZone(zone, context);

                // Get impact countdown
                String zoneWithCountdown = LocationData.getLocalizedZoneWithCountdown(zone, context);

                // Issue the notification
                RocketNotifications.notify(context, zone, zoneWithCountdown, cityNames, overrideAlertType, null);
            }
        }
    }

    static boolean zoneRecentlyNotified(String zone, Context context) {
        // Buffer time in between alerts for same zone (to prevent duplicate alerts)
        long recentCutoffTimestamp = DateTime.getUnixTimestamp() - Alerts.DUPLICATE_ALERTS_PADDING_TIME;

        // Check that enough time passed
        return AppPreferences.getZoneLastAlert(zone, context) > recentCutoffTimestamp;
    }

    public static boolean isRelevantZone(String alertType, String zone, Context context) {
        // Test or system message?
        if (isSystemTestAlert(alertType, context)) {
            return true;
        }

        // Did user select this zone? Either via regions or cities
        if (isZoneSelectedPrimarily(zone, context)) {
            return true;
        }

        // Did user select this city?
        if (isSecondaryZoneCitySelected(zone, context)) {
            return true;
        }

        // Are we nearby?
        if (isNearby(zone, context)) {
            return true;
        }

        // Irrelevant
        return false;
    }

    public static boolean isSecondaryZone(String alertType, String zone, Context context) {
        // System or test?
        if (isSystemTestAlert(alertType, context)) {
            return false;
        }

        // Did user select this area?
        if (isZoneSelectedPrimarily(zone, context)) {
            return false;
        }

        // Not a nearby zone?
        if (isNearby(zone, context)) {
            return false;
        }

        // Did user select this zone?
        if (isSecondaryZoneCitySelected(zone, context)) {
            return true;
        }

        // If we are here, not selected
        return false;
    }

    public static boolean isZoneSelectedPrimarily(String zone, Context context) {
        // Get enabled / disabled setting
        boolean notificationsEnabled = AppPreferences.getNotificationsEnabled(context);

        // Are real time alerts disabled?
        if (!notificationsEnabled) {
            return false;
        }

        // Get user's selected regions
        String selectedRegions = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedRegionsPref), context.getString(R.string.none));

        // All are selected?
        if (StringUtils.stringIsNullOrEmpty(selectedRegions) || selectedRegions.equals(context.getString(R.string.all))) {
            return true;
        }

        // Explode regions into array
        List<String> selectedRegionsList = Arrays.asList(selectedRegions.split(","));

        // Cache alert region for loop
        String alertRegion = LocationData.GetZoneRegion(zone);

        // Traverse selected regions
        for (String selectedRegion : selectedRegionsList) {
            // Does alert region contain the selected region?
            if (selectedRegion.length() > 2 && alertRegion.contains(selectedRegion)) {
                return true;
            }
            // Does alert region start with the selected region? (fix for "דן" matching "הירדן")
            else if (selectedRegion.length() <= 2 && alertRegion.startsWith(selectedRegion)) {
                return true;
            }
        }

        // Get user's selected cities
        String selectedCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedCitiesPref), context.getString(R.string.none));

        // All selected?
        if (StringUtils.stringIsNullOrEmpty(selectedCities) || selectedCities.equals(context.getString(R.string.all))) {
            return true;
        }

        // Explode into array
        List<String> selectedCityCodes = LocationData.getSelectedCityCodes(selectedCities);

        // Selected a city with this code?
        if (selectedCityCodes.contains(LocationData.getZoneCode(zone))) {
            // We got a match!
            return true;
        }

        // No match
        return false;
    }

    public static boolean isSecondaryZoneCitySelected(String zone, Context context) {
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
        String secondaryCities = Singleton.getSharedPreferences(context).getString(context.getString(R.string.selectedSecondaryCitiesPref), context.getString(R.string.all));

        // All selected?
        if (StringUtils.stringIsNullOrEmpty(secondaryCities) || secondaryCities.equals(context.getString(R.string.all))) {
            return true;
        }

        // Get selected zone codes
        List<String> selectedCityCodes = LocationData.getSelectedCityCodes(secondaryCities);

        // Selected a city with this code?
        if (selectedCityCodes.contains(LocationData.getZoneCode(zone))) {
            // We got a match!
            return true;
        }

        // No match
        return false;
    }

    public static boolean isNearby(String zone, Context context) {
        // Get nearby alerts enabled setting
        boolean nearbyEnabled = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.locationAlertsPref), false);

        // Are nearby alerts enabled?
        if (!nearbyEnabled) {
            return false;
        }

        // Get current location (last known)
        Location myLocation = LocationLogic.getLocation(context);

        // No recent location?
        if (myLocation == null) {
            return false;
        }

        // Get zone locations
        List<Location> locations = LocationData.getCityLocationsByZone(zone, context);

        // Calculate max distance
        double maxDistance = LocationLogic.getMaxDistanceKilometers(context, -1);

        // Loop over returned locations
        for (Location location : locations) {
            // Get distance to city in KM
            float distance = location.distanceTo(myLocation) / 1000;

            // Distance is less than max?
            if (distance <= maxDistance) {
                // We are nearby!
                return true;
            }
        }

        // No match
        return false;
    }

    public static boolean isSystemTestAlert(String alertType, Context context) {
        // Self-test?
        if (alertType.equals(AlertTypes.TEST)) {
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
