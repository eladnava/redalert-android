package com.red.alert.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.red.alert.R;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.model.metadata.City;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.localization.rtl.adapters.RTLMarkerInfoWindowAdapter;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;

import java.util.List;

public class AlertView extends AppCompatActivity {
    GoogleMap mMap;

    String mAlertZone;
    String mAlertDateString;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize alert
        unpackExtras();

        // Initialize UI
        initializeUI();

        // Initialize map
        initializeMap();
    }

    void unpackExtras() {
        // Get alert area
        mAlertZone = getIntent().getStringExtra(AlertViewParameters.ALERT_ZONE);

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
                mMap.setMyLocationEnabled(true);

                // Wait for tiles to load
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Add map overlays
                        addOverlays();
                    }
                }, 500);
            }
        });
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
        // Get alert area
        List<City> cities = LocationData.getCitiesByZone(mAlertZone, this);

        // No cities found?
        if (cities.size() == 0) {
            return;
        }

        // Default to zoom of 8
        int zoom = 8;

        // Default to center of Israel
        LatLng location = new LatLng(31.4117256, 35.0818155);

        // Get user's locale
        boolean isEnglish = Localization.isEnglishLocale(this);

        // Traverse over cities
        for (City city : cities) {
            // No location?
            if (city.latitude == 0) {
                continue;
            }

            // Get name
            String cityName = (isEnglish) ? city.nameEnglish : city.name;

            // Get countdown
            String zoneWithCountdown = LocationData.getLocalizedZoneWithCountdown(city.zone, this);

            // Set title manually after overriding locale
            setTitle(zoneWithCountdown);

            // Set zoom
            zoom = 12;

            // Create location
            location = new LatLng(city.latitude, city.longitude);

            // Add marker to map
            mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title(cityName));
        }

        // Prepare new camera position
        CameraPosition position = new CameraPosition.Builder()
                .target(location)
                .zoom(zoom)
                .tilt(30)
                .bearing(10)
                .build();

        // Animate nicely
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1500, null);
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
        String zone = LocationData.getLocalizedZone(mAlertZone, this);

        // Construct share message
        return getString(R.string.alertSoundedAt) + " " + zone + " (" + LocationData.getCityNamesByZone(mAlertZone, this) + ") " + getString(R.string.atTime) + " " + mAlertDateString + " " + getString(R.string.alertSentVia);
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add refresh in Action Bar
        MenuItem shareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.shareAlert));

        // Set up the view
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
        // Reset activity name (after localization is loaded)
        setTitle(R.string.appName);

        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set up UI
        setContentView(R.layout.alert_view);

        // Get map instance
        mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
    }
}
