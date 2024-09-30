package com.red.alert.utils.metadata;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.text.TextUtils;
import android.util.Log;

import me.pushy.sdk.lib.jackson.core.type.TypeReference;
import com.red.alert.R;
import com.red.alert.activities.settings.alerts.LocationAlerts;
import com.red.alert.config.Alerts;
import com.red.alert.config.Logging;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.config.ThreatTypes;
import com.red.alert.model.metadata.City;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.Localization;

import org.ocpsoft.prettytime.PrettyTime;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class LocationData {
    private static List<City> mCities;
    private static boolean mFetchingCities;

    private static HashMap<String, ArrayList<ArrayList<Double>>> mPolygons;

    /*
    // Zone = דן 161
    // Zone Code = 161
    // City Name = תל אביב
    // City Countdown = 15 שניות
     */

    public static String[] getAllCityNames(Context context) {
        // Get all city objects
        List<City> cities = getAllCities(context);

        // Prepare names array
        List<String> names = new ArrayList<>();

        // Loop over cities
        for (City city : cities) {
            // Hebrew?
            if (Localization.isHebrew(context)) {
                // Add Hebrew name to list
                names.add(city.name);
            }
            // Russian?
            else if (Localization.isRussian(context)) {
                // Add Russian name to list
                names.add(city.nameRussian);
            }
            // Arabic?
            else if (Localization.isArabic(context)) {
                // Add Arabic name to list
                names.add(city.nameArabic);
            }
            else {
                // Add English name to list
                names.add(city.nameEnglish);
            }
        }

        // Return array
        return names.toArray(new String[names.size()]);
    }

    public static String[] getAllCityValues(Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Prepare array
        List<String> values = new ArrayList<String>();

        // Loop over cities
        for (City city : cities) {
            // Add to list
            values.add(city.value);
        }

        // Return array
        return values.toArray(new String[values.size()]);
    }

    public static String[] getAllCityZones(Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Prepare array
        List<String> values = new ArrayList<String>();

        // Loop over cities
        for (City city : cities) {
            // Hebrew?
            if (Localization.isHebrew(context)) {
                // Add Hebrew name to list
                values.add(city.zone);
            }
            // Russian?
            else if (Localization.isRussian(context)) {
                // Add Russian name to list
                values.add(city.zoneRussian);
            }
            // Arabic?
            else if (Localization.isArabic(context)) {
                // Add Arabic name to list
                values.add(city.zoneArabic);
            }
            else {
                // Add English name to list
                values.add(city.zoneEnglish);
            }
        }

        // Return array
        return values.toArray(new String[values.size()]);
    }

    public static List<City> getAllCities(Context context) {
        // Already fetching?
        while (mFetchingCities) {
            try {
                // Wait for operation to complete (blocking)
                Thread.sleep(100);
            } catch (Exception exc) {
                // Ignore errors
            }
        }

        // Got it in cache?
        if (mCities != null) {
            return mCities;
        }

        try {
            // Prevent concurrent loading of cities.json
            mFetchingCities = true;

            // Open cities.json for reading
            InputStream stream = context.getResources().openRawResource(R.raw.cities);

            // Create a buffered reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            // StringBuilder for efficiency
            StringBuilder builder = new StringBuilder();

            // A temporary variable to store current line
            String currentLine;

            // Read all lines
            while ((currentLine = reader.readLine()) != null) {
                // Append to builder
                builder.append(currentLine);
            }

            // Convert to string
            String json = builder.toString();

            // Convert to city objects
            mCities = Singleton.getJackson().readValue(json, new TypeReference<List<City>>() {
            });
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Failed to load cities.json", exc);
        }
        finally {
            // No longer fetching cities
            mFetchingCities = false;
        }

        // Return them
        return mCities;
    }

    public static HashMap<String, ArrayList<ArrayList<Double>>> getAllPolygons(Context context) {
        // Got it in cache?
        if (mPolygons != null) {
            return mPolygons;
        }

        try {
            // Open polygons.json for reading
            InputStream stream = context.getResources().openRawResource(R.raw.polygons);

            // Create a buffered reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            // StringBuilder for efficiency
            StringBuilder builder = new StringBuilder();

            // A temporary variable to store current line
            String currentLine;

            // Read all lines
            while ((currentLine = reader.readLine()) != null) {
                // Append to builder
                builder.append(currentLine);
            }

            // Convert to string
            String json = builder.toString();

            // Convert to HashMap object
            mPolygons = Singleton.getJackson().readValue(json, new TypeReference<HashMap<String,ArrayList<ArrayList<Double>>>>() {});
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Failed to load polygons.json", exc);
        }

        // Return polygon data
        return mPolygons;
    }

    public static String getSelectedCityNamesByValues(Context context, String selection, CharSequence[] names, CharSequence[] values) {
        // No value? All cities are selected
        if (StringUtils.stringIsNullOrEmpty(selection) || selection.equals(context.getString(R.string.all))) {
            return context.getString(R.string.allString);
        }

        // Value equals none | null?
        else if (selection.equals(context.getString(R.string.none)) || selection.equals(context.getString(R.string.nullString))) {
            return context.getString(R.string.noneString);
        }

        // Display names
        List<String> selectedCityNames = new ArrayList<String>();

        // Remove area code
        String[] selectedValues = selection.split("\\|");

        // Loop over values
        for (String selectedValue : selectedValues) {
            // Loop over values
            for (int i = 0; i < values.length; i++) {
                // Got the value?
                if (values[i].equals(selectedValue)) {
                    // Add the name to list
                    selectedCityNames.add(names[i].toString());
                }
            }
        }

        // Failed to find cities?
        if (selectedCityNames.size() == 0) {
            // Return "none"
            return context.getString(R.string.noneString);
        }

        // Implode into a CSV string
        String displayText = StringUtils.implode(", ", selectedCityNames);

        // Truncate if too long
        if (displayText.length() > 120) {
            displayText = displayText.substring(0, 120) + "...";
        }

        // Return display text
        return displayText;
    }

    public static String getLocalizedZoneWithCountdown(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Localize time to shelter
                String localizedCountdown = getLocalizedCountdown(city.countdown, context);

                // Hebrew?
                if (Localization.isHebrew(context)) {
                    // Return zone + countdown in Hebrew
                    return city.zone + " (" + localizedCountdown + ")";
                }
                else if (Localization.isRussian(context)) {
                    // Return zone + countdown in Russian
                    return city.zoneRussian  + " (" + localizedCountdown + ")";
                }
                else if (Localization.isArabic(context)) {
                    // Return zone + countdown in Arabic
                    return city.zoneArabic  + " (" + localizedCountdown + ")";
                }
                else {
                    // Return zone + countdown in English
                    return city.zoneEnglish  + " (" + localizedCountdown + ")";
                }
            }
        }

        // No match
        return "";
    }

    public static String getLocalizedCountdown(int countdown, Context context) {
        // Return localized string based on countdown seconds
        switch (countdown) {
            case 0:
                return context.getString(R.string.immediately);
            case 15:
                return context.getString(R.string.fifteenSeconds);
            case 30:
                return context.getString(R.string.thirtySeconds);
            case 45:
                return context.getString(R.string.fortyFiveSeconds);
            case 60:
                return context.getString(R.string.oneMinute);
            case 90:
                return context.getString(R.string.oneMinuteAndAHalf);
            case 180:
                return context.getString(R.string.threeMinutes);
        }

        // Fallback to immediately on unexpected countdown value
        return context.getString(R.string.immediately);
    }

    public static int getCityCountdown(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Return countdown
                return city.countdown;
            }
        }

        // No match
        return 0;
    }

    public static int getPrioritizedCountdownForCities(String[] cities, Context context) {
        // Default to highest possible countdown value (3 minutes)
        int minCountdown = 180;

        // Keep track of whether an alert exists for a primarily-selected city
        boolean primaryFound = false;

        // Traverse current alert cities to find primarily-selected / nearby city countdown, or default to shortest countdown
        for (String city : cities) {
            // Get countdown for current city in seconds
            int countdown = LocationData.getCityCountdown(city, context);

            // If city selected primarily (or it's nearby), just return its countdown value
            if (AlertLogic.isCitySelectedPrimarily(city, context) || AlertLogic.isNearby(city, context)) {
                // First primary encountered, or another primary with lower countdown?
                if (!primaryFound || countdown < minCountdown) {
                    minCountdown = countdown;
                }

                // Mark primary found
                primaryFound = true;
            }
            else if (!primaryFound) {
                // Current city's countdown is lower than current minimum?
                if (countdown < minCountdown) {
                    minCountdown = countdown;
                }
            }
        }

        // Return minimum / highest priority countdown
        return minCountdown;
    }

    public static String getLocalizedCityName(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Hebrew?
                if (Localization.isHebrew(context)) {
                    return city.name;
                }
                // Russian?
                else if (Localization.isRussian(context)) {
                    return city.nameRussian;
                }
                // Arabic?
                else if (Localization.isArabic(context)) {
                    return city.nameArabic;
                }

                // Return English name
                return city.nameEnglish;
            }
        }

        // No match
        return cityName;
    }

    public static String getLocalizedCityNamesCSV(List<String> cities, Context context) {
        // Prepare list of localized city names
        List<String> localizedCityNames = new ArrayList<>();

        // Traverse cities
        for (String city : cities) {
            // Localize city name and add to list
            localizedCityNames.add(LocationData.getLocalizedCityName(city, context));
        }

        // Return localized cities as CSV
        return TextUtils.join(", ", localizedCityNames);
    }

    public static String getLocalizedCityZonesWithCountdownCSV(List<String> cities, Context context) {
        // Prepare list of localized zones
        List<String> localizedZones = new ArrayList<>();

        // Traverse cities
        for (String city : cities) {
            // Get zone and countdown as string
            String zone = LocationData.getLocalizedZoneWithCountdown(city, context);

            // Remove duplicates and empty results
            if (!StringUtils.stringIsNullOrEmpty(zone) && !localizedZones.contains(zone)) {
                // Add new city zone and countdown to list
                localizedZones.add(zone);
            }
        }

        // Return localized zones as CSV
        return TextUtils.join(", ", localizedZones);
    }

    public static List<String> getEnglishZoneTopicNames(List<String> zoneNames, Context context) {
        // Output list
        List<String> output = new ArrayList<>();

        // Support for "all"
        if (zoneNames.size() == 1 && zoneNames.get(0).equals("all")) {
            output.add("all");
            return output;
        }

        // Prepare cities list
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (zoneNames.contains(city.zone)) {
                // Add English zone name to output list
                output.add(sanitizeTopicName(city.zoneEnglish));
            }
        }

        // All done
        return output;
    }

    public static String sanitizeTopicName(String topic) {
        // Replace non-alphabet characters with '_'
        return topic.toLowerCase().replaceAll("[^a-z]", "_").replaceAll("_+", "_");
    }

    public static List<String> getEnglishCityTopicNames(List<String> cityNames, Context context) {
        // Output list
        List<String> output = new ArrayList<>();

        // Support for "all"
        if (cityNames.size() == 1 && cityNames.get(0).equals("all")) {
            output.add("all");
            return output;
        }

        // Prepare cities list
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (cityNames.contains(city.name)) {
                // Add English zone name to output list
                output.add(sanitizeTopicName(city.nameEnglish));
            }
        }

        // All done
        return output;
    }

    public static String getLocalizedThreatType(String threatType, Context context) {
        // Result resource
        int result = 0;

        // Get resource by alert type
        switch (threatType) {
            case ThreatTypes.TEST:
                result = R.string.test;
                break;
            case ThreatTypes.MISSILES:
                result = R.string.missiles;
                break;
            case ThreatTypes.RADIOLOGICAL_EVENT:
                result = R.string.radiologicalEvent;
                break;
            case ThreatTypes.EARTHQUAKE:
                result = R.string.earthQuake;
                break;
            case ThreatTypes.TSUNAMI:
                result = R.string.tsunami;
                break;
            case ThreatTypes.HOSTILE_AIRCRAFT_INTRUSION:
                result = R.string.hostileAircraftIntrusion;
                break;
            case ThreatTypes.HAZARDOUS_MATERIALS:
                result = R.string.hazardousMaterials;
                break;
            case ThreatTypes.TERRORIST_INFILTRATION:
                result = R.string.terroristInfiltration;
                break;
            case ThreatTypes.MISSILES_DRILL:
                result = R.string.missilesDrill;
                break;
            case ThreatTypes.EARTHQUAKE_DRILL:
                result = R.string.earthQuakeDrill;
                break;
            case ThreatTypes.RADIOLOGICAL_EVENT_DRILL:
                result = R.string.radiologicalEventDrill;
                break;
            case ThreatTypes.TSUNAMI_DRILL:
                result = R.string.tsunamiDrill;
                break;
            case ThreatTypes.HOSTILE_AIRCRAFT_INTRUSION_DRILL:
                result = R.string.hostileAircraftIntrusionDrill;
                break;
            case ThreatTypes.HAZARDOUS_MATERIALS_DRILL:
                result = R.string.hazardousMaterialsDrill;
                break;
            case ThreatTypes.TERRORIST_INFILTRATION_DRILL:
                result = R.string.terroristInfiltrationDrill;
                break;
            case ThreatTypes.SYSTEM:
                result = R.string.system;
                break;
            default:
                result = R.string.unknown;
                break;
        }

        // Convert to string
        return context.getString(result);
    }
    
    public static int getThreatDrawable(String threat) {
        // Null fallback
        if (threat == null) {
            return R.drawable.ic_launcher;
        }

        // Return drawable resource by threat type
        if (threat.contains(ThreatTypes.RADIOLOGICAL_EVENT)) {
            return R.drawable.ic_radiological_event;
        }
        else if (threat.contains(ThreatTypes.HOSTILE_AIRCRAFT_INTRUSION)) {
            return R.drawable.ic_hostile_aircraft_intrusion;
        }
        else if (threat.contains(ThreatTypes.HAZARDOUS_MATERIALS)) {
            return R.drawable.ic_hazardous_materials;
        }
        else if (threat.contains(ThreatTypes.TSUNAMI)) {
            return R.drawable.ic_tsunami;
        }
        else if (threat.contains(ThreatTypes.MISSILES)) {
            return R.drawable.ic_launcher;
        }
        else if (threat.contains(ThreatTypes.TERRORIST_INFILTRATION)) {
            return R.drawable.ic_terrorist_infiltration;
        }
        else if (threat.contains(ThreatTypes.EARTHQUAKE)) {
            return R.drawable.ic_earthquake;
        }
        else {
            return R.drawable.ic_launcher;
        }
    }

    public static String getLocalizedThreatInstructions(String threatType, Context context) {
        // Result resource
        int result = 0;

        // Get resource by alert type
        switch (threatType) {
            case ThreatTypes.TEST:
                result = R.string.test;
                break;
            case ThreatTypes.MISSILES:
                result = R.string.missilesInstructions;
                break;
            case ThreatTypes.RADIOLOGICAL_EVENT:
                result = R.string.radiologicalEventInstructions;
                break;
            case ThreatTypes.EARTHQUAKE:
                result = R.string.earthQuakeInstructions;
                break;
            case ThreatTypes.TSUNAMI:
                result = R.string.tsunamiInstructions;
                break;
            case ThreatTypes.HOSTILE_AIRCRAFT_INTRUSION:
                result = R.string.hostileAircraftIntrusionInstructions;
                break;
            case ThreatTypes.HAZARDOUS_MATERIALS:
                result = R.string.hazardousMaterialsInstructions;
                break;
            case ThreatTypes.TERRORIST_INFILTRATION:
                result = R.string.terroristInfiltrationInstructions;
                break;
            case ThreatTypes.MISSILES_DRILL:
                result = R.string.missilesInstructions;
                break;
            case ThreatTypes.EARTHQUAKE_DRILL:
                result = R.string.earthQuakeInstructions;
                break;
            case ThreatTypes.RADIOLOGICAL_EVENT_DRILL:
                result = R.string.radiologicalEventInstructions;
                break;
            case ThreatTypes.TSUNAMI_DRILL:
                result = R.string.tsunamiInstructions;
                break;
            case ThreatTypes.HOSTILE_AIRCRAFT_INTRUSION_DRILL:
                result = R.string.hostileAircraftIntrusionInstructions;
                break;
            case ThreatTypes.HAZARDOUS_MATERIALS_DRILL:
                result = R.string.hazardousMaterialsInstructions;
                break;
            case ThreatTypes.TERRORIST_INFILTRATION_DRILL:
                result = R.string.terroristInfiltrationInstructions;
                break;
            case ThreatTypes.SYSTEM:
                result = R.string.system;
                break;
            default:
                result = R.string.unknown;
                break;
        }

        // Convert to string
        return context.getString(result);
    }

    public static City getCityByName(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                return city;
            }
        }

        // No match
        return null;
    }

    public static String getNearbyCityNames(Location myLocation, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<String> cityNames = new ArrayList<>();

        // Calculate max distance
        double maxDistance = LocationLogic.getMaxDistanceKilometers(context, -1);

        // Traverse all cities
        for (City city : cities) {
            // Create an empty location object
            Location location = new Location(LocationManager.PASSIVE_PROVIDER);

            // Set latitude & longitude
            location.setLatitude(city.latitude);
            location.setLongitude(city.longitude);

            // Get distance to city in KM
            float distance = location.distanceTo(myLocation) / 1000;

            // Distance is less than max?
            if (distance <= maxDistance) {
                // Hebrew?
                if (Localization.isHebrew(context)) {
                    cityNames.add(city.name);
                }
                // Russian?
                else if (Localization.isRussian(context)) {
                    cityNames.add(city.nameRussian);
                }
                // Arabic?
                else if (Localization.isArabic(context)) {
                    cityNames.add(city.nameArabic);
                }
                else {
                    // Revert to English
                    cityNames.add(city.nameEnglish);
                }
            }
        }

        // Join and add original code
        return StringUtils.implode(", ", cityNames);
    }

    public static String getDistanceFromCity(City city, Context context) {
        // Get current location
        Location myLocation = LocationLogic.getCurrentLocation(context);

        // No location?
        if (myLocation == null) {
            return "";
        }

        // Create an empty location object
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);

        // Set latitude & longitude
        location.setLatitude(city.latitude);
        location.setLongitude(city.longitude);

        // Get distance to city in KM
        float distance = location.distanceTo(myLocation) / 1000;

        // Return distance as float rounded to 2 decimal places
        return String.format("%.2f", distance);
    }

    public static List<String> getNearbyCities(Location myLocation, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<String> cityValues = new ArrayList<>();

        // Calculate max distance
        double maxDistance = LocationLogic.getMaxDistanceKilometers(context, -1);

        // Traverse all cities
        for (City city : cities) {
            // Create an empty location object
            Location location = new Location(LocationManager.PASSIVE_PROVIDER);

            // Set latitude & longitude
            location.setLatitude(city.latitude);
            location.setLongitude(city.longitude);

            // Get distance to city in KM
            float distance = location.distanceTo(myLocation) / 1000;

            // Distance is less than max?
            if (distance <= maxDistance) {
                cityValues.add(city.value);
            }
        }

        // Return as list
        return cityValues;
    }

    public static Location getCityLocation(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Create an empty location object
                Location location = new Location(LocationManager.PASSIVE_PROVIDER);

                // Set latitude & longitude
                location.setLatitude(city.latitude);
                location.setLongitude(city.longitude);

                // Add to list
                return location;
            }
        }

        // Fail
        return null;
    }

    public static List<String> explodePSV(String citiesPSV) {
        // Unique list
        List<String> uniqueList = new ArrayList<>();

        // Explode into array
        List<String> psvList = Arrays.asList(citiesPSV.split("\\|"));

        // Loop over items
        for (String item : psvList) {
            // Not already added?
            if (!StringUtils.stringIsNullOrEmpty(item) && !uniqueList.contains(item) && !item.equals("none") && !item.equals("null")) {
                // Add it
                uniqueList.add(item);
            }
        }

        // Return them
        return uniqueList;
    }

    public static String getZoneByCityName(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                return city.zone;
            }
        }

        // Unknown city
        return "";
    }

    public static String getLocalizedZoneByCityName(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Hebrew?
                if (Localization.isHebrew(context)) {
                    return city.zone;
                }
                // Russian?
                else if (Localization.isRussian(context)) {
                    return city.zoneRussian;
                }
                // Arabic?
                else if (Localization.isArabic(context)) {
                    return city.zoneArabic;
                }
                else {
                    return city.zoneEnglish;
                }
            }
        }

        // Unknown city
        return "";
    }

    public static List<String> getZonesByCityValues(List<String> cityValues, Context context) {
        // Results array
        List<String> results = new ArrayList<>();

        // Loop over cities
        for (String cityValue : cityValues) {
            // Attempt to find city by value
            City city = getCityByValue(cityValue, context);

            // Got a match?
            if (city != null) {
                // Return English name
                results.add(city.zoneEnglish.toLowerCase());
            }
        }

        // Return results
        return results;
    }

    public static City getCityByValue(String value, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.value.equals(value)) {
                return city;
            }
        }

        // No match
        return null;
    }

    public static String getAlertDateTimeString(long timestamp, long firstAlertTimestamp, Context context) {
        // Initialize date format libraries
        SimpleDateFormat dateFormat = new SimpleDateFormat(Alerts.DATE_FORMAT);

        // Convert unix timestamp to Java Date
        Date date = new Date(timestamp * 1000);

        // Result string
        String dateString;

        // Grouped alert?
        if (firstAlertTimestamp > 0 && firstAlertTimestamp != timestamp) {
            // Convert unix timestamp of first grouped alert to Date object
            Date firstAlertDate = new Date(firstAlertTimestamp * 1000);

            // Prepare string with relative time ago and fixed HH:mm:ss with both the first and last alert times
            dateString = getAlertRelativeTimeAgo(timestamp, context) + " (" + dateFormat.format(firstAlertDate) + " - " + dateFormat.format(date) + ")";
        }
        else {
            // Prepare string with relative time ago and fixed HH:mm:ss
            dateString = getAlertRelativeTimeAgo(timestamp, context) + " (" + dateFormat.format(date) + ")";
        }

        // Convert Arabic numerals to digits if needed
        dateString = Localization.localizeDigits(dateString, context);

        // All done
        return dateString;
    }

    public static String getAlertRelativeTimeAgo(long timestamp, Context context) {
        // Initialize date format libraries
        PrettyTime relativeFormat = new PrettyTime(context.getResources().getConfiguration().locale);

        // Convert unix timestamp to Java Date
        Date date = new Date(timestamp * 1000);

        // Prepare string with relative time ago
        String dateString = StringUtils.capitalize(relativeFormat.format(date));

        // Fix for Hebrew relative time typos (singular time ago)
        dateString = dateString.replace(" 1 דקה", " דקה");
        dateString = dateString.replace(" 1 שעה", " שעה");
        dateString = dateString.replace(" 2 שעות", " שעתיים");

        // Convert Arabic numerals to digits if needed
        dateString = Localization.localizeDigits(dateString, context);

        // All done
        return dateString;
    }
}
