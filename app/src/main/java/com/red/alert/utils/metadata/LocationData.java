package com.red.alert.utils.metadata;

import android.content.Context;
import android.util.Log;

import me.pushy.sdk.lib.jackson.core.type.TypeReference;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.model.metadata.City;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.Localization;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class LocationData {
    private static List<City> mCities;
    private static HashMap<String, ArrayList<ArrayList<Double>>> mPolygons;

    /*
    // Zone = דן 161
    // Zone Code = 161
    // City Name = תל אביב
    // City Countdown = 15 שניות
     */

    public static String[] getAllCityNames(Context context) {
        // Check for english locale
        boolean isHebrew = Localization.isHebrewLocale(context);

        // Get all city objects
        List<City> cities = getAllCities(context);

        // Prepare names array
        List<String> names = new ArrayList<>();

        // Loop over cities
        for (City city : cities) {
            // Hebrew?
            if (isHebrew) {
                // Add to list
                names.add(city.name);
            }
            else if (Localization.isRussian(context)) {
                // Add russian name to list
                names.add(city.nameRussian);
            }
            else {
                // Add english name to list
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
        // Get user's locale
        boolean isHebrew = Localization.isHebrewLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Prepare array
        List<String> values = new ArrayList<String>();

        // Loop over cities
        for (City city : cities) {
            // Hebrew?
            if (isHebrew) {
                // Add to list
                values.add(city.zone);
            }
            else if (Localization.isRussian(context)) {
                // Add russian name to list
                values.add(city.zoneRussian);
            }
            else {
                // Add to list
                values.add(city.zoneEnglish);
            }
        }

        // Return array
        return values.toArray(new String[values.size()]);
    }

    public static List<City> getAllCities(Context context) {
        // Got it in cache?
        if (mCities != null) {
            return mCities;
        }

        try {
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
        // Get user's locale
        boolean isHebrew = Localization.isHebrewLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Hebrew?
                if (isHebrew) {
                    // Return area countdown
                    return city.zone + " (" + city.time + ")";
                }
                else if (Localization.isRussian(context)) {
                    // Return area countdown in russian
                    return city.zoneRussian  + " (" + city.timeRussian + ")";
                }
                else {
                    // Return area countdown in english
                    return city.zoneEnglish  + " (" + city.timeEnglish + ")";
                }
            }
        }

        // No match
        return "";
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

    public static String getLocalizedCityName(String cityName, Context context) {
        // Get user's locale
        boolean isHebrew = Localization.isHebrewLocale(context);

        // Hebrew?
        if (isHebrew) {
            return cityName;
        }

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Check for russian first
                if (Localization.isRussian(context)) {
                    return city.nameRussian;
                }

                // Return english name
                return city.nameEnglish;
            }
        }

        // No match
        return cityName;
    }

    public static List<String> getEnglishZoneTopicNames(List<String> hebrewZoneNames, Context context) {
        // Output list
        List<String> output = new ArrayList<>();

        // Support for "all"
        if (hebrewZoneNames.size() == 1 && hebrewZoneNames.get(0).equals("all")) {
            output.add("all");
            return output;
        }

        // Prepare cities list
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (hebrewZoneNames.contains(city.zone)) {
                // Add english zone name to output list
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

    public static List<String> getEnglishCityTopicNames(List<String> hebrewCityNames, Context context) {
        // Output list
        List<String> output = new ArrayList<>();

        // Support for "all"
        if (hebrewCityNames.size() == 1 && hebrewCityNames.get(0).equals("all")) {
            output.add("all");
            return output;
        }

        // Prepare cities list
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (hebrewCityNames.contains(city.name)) {
                // Add english zone name to output list
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
        // Get user's locale
        boolean isHebrew = Localization.isHebrewLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Add localized zone
                if (isHebrew) {
                    return city.zone;
                } else if (Localization.isRussian(context)) {
                    return city.zoneRussian;
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
                // Return english name
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
}
