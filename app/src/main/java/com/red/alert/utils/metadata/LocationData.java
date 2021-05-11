package com.red.alert.utils.metadata;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import me.pushy.sdk.lib.jackson.core.type.TypeReference;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.model.metadata.City;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.Localization;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LocationData {
    private static List<City> mCities;

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

        // Initialize the list
        mCities = new ArrayList<>();

        try {
            // Open the cities.json for reading
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
                else {
                    // Return area countdown in English
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
                // Return english name
                return city.nameEnglish;
            }
        }

        // No match
        return cityName;
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
        // Get user's locale
        boolean isHebrew = Localization.isHebrewLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<String> cityNames = new ArrayList<>();

        // Calculate max distance
        double maxDistance = LocationLogic.getMaxDistanceKilometers(context, -1);

        // String is not empty
        for (City city : cities) {
            // Prepare new location
            Location location = new Location("");

            // Set lat & long
            location.setLatitude(city.latitude);
            location.setLongitude(city.longitude);

            // Get distance to city in KM
            float distance = location.distanceTo(myLocation) / 1000;

            // Distance is less than max?
            if (distance <= maxDistance) {
                // Hebrew?
                if (isHebrew) {
                    // Add to list
                    cityNames.add(city.name);
                }
                else {
                    // Add to list
                    cityNames.add(city.nameEnglish);
                }
            }
        }

        // Join and add original code
        return StringUtils.implode(", ", cityNames);
    }

    public static Location getCityLocation(String cityName, Context context) {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Loop over cities
        for (City city : cities) {
            // Got a match?
            if (city.name.equals(cityName)) {
                // Prepare new location
                Location location = new Location(LocationManager.PASSIVE_PROVIDER);

                // Set lat & long
                location.setLatitude(city.latitude);
                location.setLongitude(city.longitude);

                // Add to list
                return location;
            }
        }

        // Fail
        return null;
    }

    public static List<String> explodeCitiesPSV(String citiesPSV) {
        // Unique cities list
        List<String> uniqueList = new ArrayList<>();

        // Explode into array
        List<String> psvList = Arrays.asList(citiesPSV.split("\\|"));

        // Loop over items
        for (String item : psvList) {
            // Not already added?
            if (!uniqueList.contains(item)) {
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
                return isHebrew ? city.zone: city.zoneEnglish;
            }
        }

        // Unknown city
        return "";
    }
}
