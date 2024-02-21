package com.red.alert.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.red.alert.R;
import com.red.alert.activities.settings.alerts.LocationAlerts;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.model.Alert;
import com.red.alert.model.metadata.City;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.localization.rtl.adapters.RTLMarkerInfoWindowAdapter;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;
import me.pushy.sdk.lib.jackson.core.type.TypeReference;

public class Map extends AppCompatActivity {
    GoogleMap mMap;
    List<Alert> mAlerts;

    MenuItem mShareItem;
    MenuItem mLoadingItem;
    RelativeLayout mMapCover;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize alert
        unpackExtras();

        // Initialize UI
        initializeUI();
    }

    void unpackExtras() {
        try {
            // Parse grouped alerts from JSON
            mAlerts = Singleton.getJackson().readValue(getIntent().getStringExtra(AlertViewParameters.ALERTS), new TypeReference<List<Alert>>() {
            });
        } catch (IOException e) {
            // Show error dialog
            AlertDialogBuilder.showGenericDialog(getString(R.string.error), e.getMessage(), getString(R.string.okay), null, false, Map.this, null);
        }
    }

    void mapLoadedListener() {
        // Wait for map to load
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition arg0) {
                // Prevent from being called again
                mMap.setOnCameraChangeListener(null);

                // Fix RTL bug
                mMap.setInfoWindowAdapter(new RTLMarkerInfoWindowAdapter(getLayoutInflater()));

                // Show my location button
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }

                // Set activity title
                setActivityTitle();

                // Load polygon data async
                new LoadPolygonData().execute();
            }
        });
    }

    void setActivityTitle() {
        // Get first alert object
        Alert firstAlert = mAlerts.get(0);

        // Set title to alert relative time ago
        setTitle(LocationData.getAlertRelativeTimeAgo(firstAlert.date, this));

        // Add localized threat type to title
        if (!StringUtils.stringIsNullOrEmpty(firstAlert.localizedThreat)) {
            setTitle(firstAlert.localizedThreat + ": " + getTitle());
        }

        // Display special title for nearby cities display
        if (firstAlert.threat.equals(ThreatTypes.NEARBY_CITIES_DISPLAY)) {
            setTitle(getString(R.string.nearbyCities) + ": " + LocationLogic.getMaxDistanceKilometers(this, -1) + " " + getString(R.string.kilometer));
        }
    }

    void initializeMap() {
        // Get map instance
        if (mMap == null) {
            // Stop execution
            return;
        }

        // Check if night mode is enabled
        if ((getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            // Use dark map styling
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style_dark));
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

        // Traverse alerts
        for (Alert alert : mAlerts) {
            // Get city object
            City city = LocationData.getCityByName(alert.city, this);

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
                String tooltip = LocationData.getAlertDateTimeString(alert.date, 0, this);

                // Add shelter count if exists for this city
                if (city.shelters > 0) {
                    tooltip += "\n" + getString(R.string.lifeshieldShelters) + " " + city.shelters;
                }

                // Custom tooltip for nearby cities display
                if (alert.threat.equals(ThreatTypes.NEARBY_CITIES_DISPLAY)) {
                    // Display current distance from city center
                    tooltip = LocationData.getDistanceFromCity(city, this) + " " + getString(R.string.kilometer);
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
        final LatLngBounds bounds = builder.build();

        // Set padding/offset from the edges of the screen (px)
        final int padding = 200;

        // Set max zoom for animation to 13
        mMap.setMaxZoomPreference(13);

        try {
            // Delay animation by 500ms
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
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
            }, 500);
        } catch (Exception exc) {
            // Ignore exceptions
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu) {
        // Add share button
        initializeShareButton(OptionsMenu);

        // Add loading indicator
        initializeLoadingIndicator(OptionsMenu);

        // Show the menu
        return true;
    }

    private String getShareMessage() {
        // Construct share message
        return mAlerts.get(0).localizedThreat + " " + getString(R.string.alertSoundedAt) + mAlerts.get(0).localizedCity + "\n" + mAlerts.get(0).dateString + "\n\n" + getString(R.string.alertSentVia);
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add share icon to Action Bar
        mShareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.shareAlert));

        // Set share icon
        mShareItem.setIcon(R.drawable.ic_share);

        // Hide by default
        mShareItem.setVisible(false);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mShareItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, open share
        mShareItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
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

        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set up UI
        setContentView(R.layout.alert_view);

        // Store reference to map cover for later
        mMapCover = (RelativeLayout) findViewById(R.id.mapCover);

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

    void initializeLoadingIndicator(Menu OptionsMenu) {
        // Add loading spinner to Action Bar
        mLoadingItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.loading));

        // Set up the view
        MenuItemCompat.setActionView(mLoadingItem, R.layout.loading);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mLoadingItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Hide by default
        mLoadingItem.setVisible(false);
    }

    public class LoadPolygonData extends AsyncTaskAdapter<Integer, String, Integer> {
        public LoadPolygonData() {
            // Show loading indicator
            mLoadingItem.setVisible(true);
        }

        @Override
        protected Integer doInBackground(Integer... Parameter) {
            // Load polygons data for all cities (execution takes roughly 1 second)
            LocationData.getAllPolygons(Map.this);

            // No errors
            return 0;
        }

        @Override
        protected void onPostExecute(Integer error) {
            // Activity dead?
            if (isFinishing()) {
                return;
            }

            // Add city polygons and markers
            addOverlays();

            // Hide loading indicator
            mLoadingItem.setVisible(false);

            // Show share button
            mShareItem.setVisible(true);

            // Delay by 200ms
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Prevent white flicker as map loads in dark mode
                    mMapCover.setVisibility(View.GONE);
                }
            }, 200);
        }
    }
}
