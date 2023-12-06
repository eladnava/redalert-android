package com.red.alert.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.red.alert.R;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.model.metadata.City;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.localization.rtl.adapters.RTLMarkerInfoWindowAdapter;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AlertView extends AppCompatActivity {
    GoogleMap mMap;

    String mAlertThreat;
    String[] mAlertCities;
    String mAlertDateString;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize alert
        unpackExtras();

        // Initialize UI
        initializeUI();
    }

    void unpackExtras() {
        // Get alert cities
        mAlertCities = getIntent().getStringArrayExtra(AlertViewParameters.ALERT_CITIES);

        // Get alert threat type
        mAlertThreat = getIntent().getStringExtra(AlertViewParameters.ALERT_THREAT);

        // Get alert date string
        mAlertDateString = getIntent().getStringExtra(AlertViewParameters.ALERT_DATE_STRING);
    }

    void mapLoadedListener() {
        // Wait for map to load
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition arg0) {
                // Prevent from being called again
                mMap.setOnCameraChangeListener(null);

                // Fix RTL bug with hebrew
                mMap.setInfoWindowAdapter(new RTLMarkerInfoWindowAdapter(getLayoutInflater()));

                // Show my location button
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }

                // Set activity title
                setActivityTitle();

                // Add map overlays
                addOverlays();
            }
        });
    }

    void setActivityTitle() {
        ArrayList<String> localizedCityNames = new ArrayList();

        // Traverse cities
        for (String cityName : mAlertCities) {
            // Get city object
            City city = LocationData.getCityByName(cityName, this);

            // No city found?
            if (city == null) {
                localizedCityNames.add(cityName);
                continue;
            }

            // Add localized name
            localizedCityNames.add(LocationData.getLocalizedCityName(city.name, this));
        }

        // Set title manually after overriding locale
        setTitle(TextUtils.join(", ", localizedCityNames));

        // Localize threat type
        String threat = LocationData.getLocalizedThreatType(mAlertThreat, this);

        // Add localized threat type to title
        if (!StringUtils.stringIsNullOrEmpty(threat)) {
            setTitle(threat + ": " + getTitle());
        }
    }

    void initializeMap() {
        // Get map instance
        if (mMap == null) {
            // Stop execution
            return;
        }

        // Wait for map to load
        mapLoadedListener();
    }

    void addOverlays() {
        // Prepare a boundary with all geo-located cities
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        // Keep track of whether any cities could be geo-located
        boolean cityFound = false;

        // Keep track of unique marker coordinates
        ArrayList<String> uniqueCoordinates = new ArrayList<>();

        // Get polygons data for all cities (execution takes roughly 1 second)
        HashMap<String, ArrayList<ArrayList<Double>>> polygons = LocationData.getAllPolygons(this);

        // Traverse cities
        for (String cityName : mAlertCities) {
            // Get city object
            City city = LocationData.getCityByName(cityName, this);

            // No city found?
            if (city == null) {
                continue;
            }

            // Check if we have polygon data for this city
            if (polygons.containsKey(String.valueOf(city.id))) {
                // Get polygons for city
                ArrayList<ArrayList<Double>> cityPolygons = polygons.get(String.valueOf(city.id));

                // Prepare list of LatLng objects with all polygon points
                List<LatLng> polygonPoints = new ArrayList<>();

                // Prepare a boundary calculator to find polygon center point
                LatLngBounds.Builder polygonCenter = new LatLngBounds.Builder();

                // Traverse polygon points
                for (ArrayList<Double> polygonPoint : cityPolygons) {
                    // Create new LatLng object for each point
                    LatLng point = new LatLng(polygonPoint.get(0), polygonPoint.get(1));

                    // Include in ArrayList
                    polygonPoints.add(point);

                    // Include in LatLngBounds
                    polygonCenter.include(point);
                }

                // Add city polygon to map with custom styling
                mMap.addPolygon(new PolygonOptions()
                        .clickable(true)
                        .strokeWidth(4)
                        .strokeColor(0xffe40000)
                        .fillColor(0xb3ffafaf)
                        .addAll(polygonPoints));

                // Get polygon center point
                LatLngBounds bounds = polygonCenter.build();

                // Use center of polygon instead of default city location
                LatLng center = bounds.getCenter();

                // Override city lat, lng with polygon center point
                city.latitude = center.latitude;
                city.longitude = center.longitude;
            }

            // Does this city have a geolocation?
            if (city.latitude != 0) {
                // Get localized city and zone names
                String localizedName = LocationData.getLocalizedCityName(city.name, this) + " (" + LocationData.getLocalizedZoneByCityName(city.name, this) + ")";

                // Already have a marker with these exact coordinates?
                while (uniqueCoordinates.indexOf(city.latitude + "-" + city.longitude) != -1) {
                    city.latitude += 0.01;
                    city.longitude += 0.01;
                }

                // Create LatLng location object
                LatLng location = new LatLng(city.latitude, city.longitude);

                // Marker tooltip
                String tooltip = mAlertDateString;

                // Add shelter count if exists for this city
                if (city.shelters > 0) {
                    tooltip += "\n" + getString(R.string.lifeshieldShelters) + " " + city.shelters;
                }

                // Add marker to map
                mMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title(localizedName)
                        .snippet(tooltip));

                // Include location in zoom boundaries
                builder.include(location);

                // We found at least one city
                cityFound = true;

                // Avoid adding another marker at these exact coordinates
                uniqueCoordinates.add(city.latitude + "-" + city.longitude);
            }
        }

        // Geolocation failure?
        if (!cityFound) {
            return;
        }

        // Build a boundary for the map positioning
        LatLngBounds bounds = builder.build();

        // Set padding/offset from the edges of the screen (px)
        int padding = 200;

        // Set max zoom for animation to 13
        mMap.setMaxZoomPreference(13);

        try {
            // Animate nicely
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1500, new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    // Allow user to zoom in freely
                    mMap.resetMinMaxZoomPreference();
                }

                @Override
                public void onCancel() {
                }
            });
        }
        catch (Exception exc) {
            // Ignore exceptions
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu) {
        // Add share button
        initializeShareButton(OptionsMenu);

        // Show the menu
        return true;
    }

    private String getShareMessage() {
        // Get zone name
        String cityName = LocationData.getLocalizedCityName(TextUtils.join(", ", mAlertCities), this);

        // Construct share message
        return getString(R.string.alertSoundedAt) + getTitle() + " " + mAlertDateString + " " + getString(R.string.alertSentVia);
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add refresh in Action Bar
        MenuItem shareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.shareAlert));

        // Set share icon
        shareItem.setIcon(R.drawable.ic_share);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(shareItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, open share
        shareItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Prepare share intent
                Intent shareIntent = new Intent(Intent.ACTION_SEND);

                // Set as text/plain
                shareIntent.setType("text/plain");

                // Add text
                shareIntent.putExtra(Intent.EXTRA_TEXT, getShareMessage());

                // Show chooser
                startActivity(Intent.createChooser(shareIntent, getString(R.string.shareAlert)));

                // Consume event
                return true;
            }
        });
    }

    public boolean onOptionsItemSelected(final MenuItem Item) {
        // Check item ID
        switch (Item.getItemId()) {
            // Home button?
            case android.R.id.home:
                onBackPressed();
        }

        return super.onOptionsItemSelected(Item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Clear notifications and stop sound from playing
        AppNotifications.clearAll(this);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI() {
        // Ensure the right language is displayed
        Localization.overridePhoneLocale(this);

        // Reset activity name (after localization is loaded)
        setTitle(R.string.loading);

        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set up UI
        setContentView(R.layout.alert_view);

        // Get map instance
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                // Initialize map
                initializeMap();
            }
        });
    }
}
