package com.red.alert.utils.metadata;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.model.metadata.City;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationData
{
    private static List<City> mCities;

    /*
    // Zone = דן 161
    // Zone Code = 161
    // City Name = תל אביב
    // City Countdown = 15 שניות
     */

    public static String[] getAllCityNames(Context context)
    {
        // Check for english locale
        boolean isEnglish = Localization.isEnglishLocale(context);

        // Get all city objects
        List<City> cities = getAllCities(context);

        // Prepare names array
        List<String> names = new ArrayList<>();

        // Loop over cities
        for (City city : cities)
        {
            // English?
            if (!isEnglish)
            {
                // Add to list
                names.add(city.name);
            }
            else
            {
                // Add english name to list
                names.add(city.nameEnglish);
            }
        }

        // Return array
        return names.toArray(new String[names.size()]);
    }

    public static String[] getAllCityValues(Context context)
    {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Prepare array
        List<String> values = new ArrayList<String>();

        // Loop over cities
        for (City city : cities)
        {
            // Add to list
            values.add(city.value);
        }

        // Return array
        return values.toArray(new String[values.size()]);
    }

    public static String[] getAllCityZones(Context context)
    {
        // Get user's locale
        boolean isEnglish = Localization.isEnglishLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Prepare array
        List<String> values = new ArrayList<String>();

        // Loop over cities
        for (City city : cities)
        {
            // English?
            if (!isEnglish)
            {
                // Add to list
                values.add(city.zone);
            }
            else
            {
                // Add to list
                values.add(city.zoneEnglish);
            }
        }

        // Return array
        return values.toArray(new String[values.size()]);
    }

    public static List<City> getAllCities(Context context)
    {
        // Got it in cache?
        if (mCities != null)
        {
            return mCities;
        }

        // Initialize the list
        mCities = new ArrayList<>();

        try
        {
            // Open the cities.json for reading
            InputStream stream = context.getResources().openRawResource(R.raw.cities);

            // Create a buffered reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            // StringBuilder for efficiency
            StringBuilder builder = new StringBuilder();

            // A temporary variable to store current line
            String currentLine;

            // Read all lines
            while ((currentLine = reader.readLine()) != null)
            {
                // Append to builder
                builder.append(currentLine);
            }

            // Convert to string
            String json = builder.toString();

            // Convert to city objects
            mCities = Singleton.getJackson().readValue(json, new TypeReference<List<City>>(){});
        }
        catch ( Exception exc )
        {
            // Log it
            Log.e(Logging.TAG, "Failed to load cities.json", exc);
        }

        // Return them
        return mCities;
    }

    public static String getSelectedCityNamesByValues(Context context, String selection, CharSequence[] names, CharSequence[] values)
    {
        // No value? All cities are selected
        if (StringUtils.stringIsNullOrEmpty(selection) || selection.equals(context.getString(R.string.all)))
        {
            return context.getString(R.string.allString);
        }

        // Value equals none | null?
        else if (selection.equals(context.getString(R.string.none)) || selection.equals(context.getString(R.string.nullString)))
        {
            return context.getString(R.string.noneString);
        }

        // Display names
        List<String> selectedCityNames = new ArrayList<String>();

        // Remove area code
        String[] selectedValues = selection.split(",");

        // Loop over values
        for (String selectedValue : selectedValues)
        {
            // Loop over values
            for (int i = 0; i < values.length; i++)
            {
                // Got the value?
                if (values[i].equals(selectedValue))
                {
                    // Add the name to list
                    selectedCityNames.add(names[i].toString());
                }
            }
        }

        // Failed to find cities?
        if (selectedCityNames.size() == 0)
        {
            // Return "none"
            return context.getString(R.string.noneString);
        }

        // Implode into a CSV string
        String displayText = StringUtils.implode(", ", selectedCityNames);

        // Truncate if too long
        if (displayText.length() > 120)
        {
            displayText = displayText.substring(0, 120) + "...";
        }

        // Return display text
        return displayText;
    }

    public static String getLocalizedZoneWithCountdown(String zone, Context context)
    {
        // Get user's locale
        boolean isEnglish = Localization.isEnglishLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Cache zone code (numeric)
        String zoneCode = getZoneCode(zone);

        // Loop over cities
        for (City city : cities)
        {
            // Got a match?
            if (city.codes.contains(zoneCode))
            {
                // Hebrew?
                if (!isEnglish)
                {
                    // Return area countdown
                    return zone + " (" + city.time + ")";
                }
                else
                {
                    // Return area without countdown but in English
                    return city.zoneEnglish;
                }
            }
        }

        // No match
        return zone;
    }

    public static int getZoneCountdown(String zone, Context context)
    {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Cache zone code
        String zoneCode = getZoneCode(zone);

        // Loop over cities
        for (City city : cities)
        {
            // Got a match?
            if (city.codes.contains(zoneCode))
            {
                // Return countdown
                return city.countdown;
            }
        }

        // No match
        return 0;
    }

    public static String getLocalizedZone(String zone, Context context)
    {
        // Get user's locale
        boolean isEnglish = Localization.isEnglishLocale(context);

        // Hebrew?
        if (!isEnglish)
        {
            return zone;
        }

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Cache zone code
        String zoneCode = getZoneCode(zone);

        // Loop over cities
        for (City city : cities)
        {
            // Got a match?
            if (city.codes.contains(zoneCode))
            {
                // Return english code
                return city.zoneEnglish;
            }
        }

        // No match
        return zone;
    }

    public static String getCityNamesByZone(String Zone, Context context)
    {
        // Get user's locale
        boolean isEnglish = Localization.isEnglishLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<String> cityNames = new ArrayList<String>();

        // Cache zone code
        String zoneCode = getZoneCode(Zone);

        // Loop over cities
        for (City city : cities)
        {
            // Got a match?
            if (city.codes.contains(zoneCode))
            {
                // English?
                if (!isEnglish)
                {
                    // Add to list
                    cityNames.add(city.name);
                }
                else
                {
                    // Add to list
                    cityNames.add(city.nameEnglish);
                }
            }
        }

        // Join and add original code
        return StringUtils.implode(", ", cityNames);
    }

    public static String getNearbyCityNames(Location myLocation, Context context)
    {
        // Get user's locale
        boolean isEnglish = Localization.isEnglishLocale(context);

        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<String> cityNames = new ArrayList<String>();

        // Calculate max distance
        double maxDistance = LocationLogic.getMaxDistanceKilometers(context, -1);

        // String is not empty
        for (City city : cities)
        {
            // Prepare new location
            Location location = new Location("");

            // Set lat & long
            location.setLatitude(city.latitude);
            location.setLongitude(city.longitude);

            // Get distance to city in KM
            float distance = location.distanceTo(myLocation) / 1000;

            // Distance is less than max?
            if (distance <= maxDistance)
            {
                // English?
                if (!isEnglish)
                {
                    // Add to list
                    cityNames.add(city.name);
                }
                else
                {
                    // Add to list
                    cityNames.add(city.nameEnglish);
                }
            }
        }

        // Join and add original code
        return StringUtils.implode(", ", cityNames);
    }

    public static List<Location> getCityLocationsByZone(String zone, Context context)
    {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<Location> locations = new ArrayList<Location>();

        // Cache zone code
        String zoneCode = getZoneCode(zone);

        // Loop over cities
        for (City city : cities)
        {
            // Got a match?
            if (city.codes.contains(zoneCode))
            {
                // Prepare new location
                Location location = new Location(LocationManager.PASSIVE_PROVIDER);

                // Set lat & long
                location.setLatitude(city.latitude);
                location.setLongitude(city.longitude);

                // Add to list
                locations.add(location);
            }
        }

        // Return locations
        return locations;
    }

    public static List<City> getCitiesByZone(String zone, Context context)
    {
        // Prepare cities array
        List<City> cities = getAllCities(context);

        // Output array
        List<City> filteredCities = new ArrayList<City>();

        // Cache zone code
        String zoneCode = getZoneCode(zone);

        // Loop over cities
        for (City city : cities)
        {
            // Got a match?
            if (city.codes.contains(zoneCode))
            {
                // Add to list
                filteredCities.add(city);
            }
        }

        // Return locations
        return filteredCities;
    }

    public static List<String> explodeZonesCSV(String zonesCSV)
    {
        // Unique list
        List<String> uniqueList = new ArrayList<String>();

        // Explode into array
        List<String> csvList = Arrays.asList(zonesCSV.split(", "));

        // Loop over items
        for (String item : csvList)
        {
            // Not already added?
            if (!uniqueList.contains(item))
            {
                // Add it
                uniqueList.add(item);
            }
        }

        // Return them
        return uniqueList;
    }

    public static List<String> getSelectedCityCodes(String selectedCities)
    {
        // Result list
        List<String> codes = new ArrayList<>();

        // Explode into array
        List<String> cityValuesList = Arrays.asList(selectedCities.split(","));

        // Loop over selected city values
        for (String city : cityValuesList)
        {
            // Get current city codes
            List<String> cityCodes = getAllCityZones(city);

            // Traverse codes
            for (String code : cityCodes)
            {
                // First time?
                if (!codes.contains(code))
                {
                    // Add code
                    codes.add(code);
                }
            }
        }

        // Return clean codes
        return codes;
    }

    public static String GetZoneRegion(String zone)
    {
        // Remove code and trim
        return zone.replaceAll("\\s?([0-9]+)", "").trim();
    }

    public static String getZoneCode(String zone)
    {
        // Remove code and trim
        return StringUtils.regexMatch(zone, ".+?\\s?([0-9]+)").trim();
    }

    public static List<String> getAllCityZones(String cityValue)
    {
        // Compile regex
        Pattern regexPattern = Pattern.compile("\\((.+?)\\)");

        // Match against source input
        Matcher regexMatcher = regexPattern.matcher(cityValue);

        // Prepare match object
        List<String> codes = new ArrayList<>();

        // Did we find anything?
        while (regexMatcher.find())
        {
            // Get first group
            codes.add(getZoneCode(regexMatcher.group(1).trim()));
        }

        // Return codes
        return codes;
    }
}
