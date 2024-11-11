package com.red.alert.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.config.RecentAlerts;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.Alert;
import com.red.alert.model.metadata.City;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.localization.rtl.adapters.RTLMarkerInfoWindowAdapter;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.MenuItemCompat;
import me.pushy.sdk.lib.jackson.core.type.TypeReference;

public class Map extends AppCompatActivity implements OnMapsSdkInitializedCallback {
    GoogleMap mMap;

    List<Alert> mAlerts;
    List<Alert> mDisplayAlerts;

    boolean mLiveMap;
    boolean mIsMapReady;
    boolean mIsResumed;
    boolean mIsReloading;

    int mDisplayedAlertsCount;

    ImageView mAppIcon;
    MenuItem mShareItem;
    MenuItem mLoadingItem;
    RelativeLayout mMapCover;
    MenuItem mClearRecentAlertsItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use legacy maps renderer to fix blank map bug for 1% of users
        useLegacyRenderer();

        // Initialize alert
        unpackExtras();

        // Initialize UI
        initializeUI();
    }

    void useLegacyRenderer() {
        // Use background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Use legacy maps renderer
                MapsInitializer.initialize(Map.this, MapsInitializer.Renderer.LEGACY, Map.this);
            }
        }).start();
    }

    void unpackExtras() {
        // Live map mode
        mLiveMap = getIntent().getBooleanExtra(AlertViewParameters.LIVE, false);

        try {
            // Parse grouped alerts from JSON
            mAlerts = Singleton.getJackson().readValue(getIntent().getStringExtra(AlertViewParameters.ALERTS), new TypeReference<List<Alert>>() {});
        } catch (Exception e) {
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

        // Live map?
        if (mLiveMap) {
            pollRecentAlerts();
        }
    }

    void setActivityTitle() {
        // Live map?
        if (mLiveMap) {
            // Set recent alerts title
            setTitle(getString(R.string.recentAlerts));
        }

        // Have at least one alert?
        else if (mAlerts.size() > 0) {
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
        else {
            // Nearby cities display and no nearby cities
            setTitle(getString(R.string.noNearbyCities));
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

    void redrawOverlays() {
        // Workaround for ConcurrentModificationException
        // Wait for reloading to complete
        if (mIsReloading || !mIsMapReady) {
            return;
        }

        // Clear currently-displayed alerts list
        mDisplayAlerts.clear();

        // Clear all existing map annotations
        mMap.clear();

        // Prepare a boundary with all geo-located cities
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        // Keep track of whether any cities could be geo-located
        boolean cityFound = false;

        // Keep track of unique marker coordinates
        ArrayList<String> uniqueCoordinates = new ArrayList<>();

        // Get polygons data for all cities (execution takes roughly 1 second)
        HashMap<String, ArrayList<ArrayList<Double>>> polygons = LocationData.getAllPolygons(this);

        // List of processed cities
        List<String> cityNames = new ArrayList<String>();

        // Check if user cleared the recent alerts
        long cutoffTimestamp = AppPreferences.getRecentAlertsCutoffTimestamp(this);

        // Traverse alerts
        for (Alert alert : mAlerts) {
            // Remove alerts that occurred before the cutoff
            if (mLiveMap && cutoffTimestamp > 0 && alert.date <= cutoffTimestamp) {
                continue;
            }

            // Get city object
            City city = LocationData.getCityByName(alert.city, this);

            // No city found?
            if (city == null) {
                continue;
            }

            // Only display one marker for each city
            if (cityNames.contains(alert.city)) {
                continue;
            }

            // Keep track of city name to avoid duplicates
            cityNames.add(alert.city);

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

            // Set to threat drawable (unless in live map mode)
            if (!mLiveMap) {
                mAppIcon.setImageResource(LocationData.getThreatDrawable(alert.threat));
            }

            // Add to display alerts
            mDisplayAlerts.add(alert);
        }

        // Reveal icon
        mAppIcon.setVisibility(View.VISIBLE);

        // Update displayed icon according to how many alerts are being displayed
        updateClearAlertsButton(cutoffTimestamp);

        // Geolocation failure?
        if (!cityFound) {
            return;
        }

        // Live map?
        // Don't move map if no change in alerts
        if (mLiveMap && mDisplayAlerts.size() == mDisplayedAlertsCount) {
            return;
        }

        // Keep track of currently displayed alert count
        mDisplayedAlertsCount = mDisplayAlerts.size();

        // Build a boundary for the map positioning
        final LatLngBounds bounds = builder.build();

        // Set padding/offset from the edges of the screen (px)
        final int padding = 200;

        // Set max zoom for animation to 13
        mMap.setMaxZoomPreference(13);

        // Delay animation by 500ms
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
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
                } catch (Exception exc) {
                    // Ignore rare exception "View size is too small after padding is applied"
                }
            }
        }, 500);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu OptionsMenu) {
        // Add clear recent alerts button
        initializeClearRecentAlertsButton(OptionsMenu);

        // Add share button
        initializeShareButton(OptionsMenu);

        // Add loading indicator
        initializeLoadingIndicator(OptionsMenu);

        // Show the menu
        return true;
    }

    void initializeClearRecentAlertsButton(Menu OptionsMenu) {
        // Only display button in live map mode
        if (!mLiveMap) {
            return;
        }

        // Add clear item
        mClearRecentAlertsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.clearRecentAlerts));

        // Set default icon
        mClearRecentAlertsItem.setIcon(R.drawable.ic_clear);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mClearRecentAlertsItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, toggle clearing recent alerts
        mClearRecentAlertsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Clear currently displayed alerts
                if (mDisplayAlerts.size() > 0) {
                    // Show a dialog to the user
                    AlertDialogBuilder.showGenericDialog(getString(R.string.clearRecentAlerts), getString(R.string.clearRecentAlertsDesc), getString(R.string.yes), getString(R.string.no), true, Map.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            // Clicked okay?
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                // Set recent alerts cutoff timestamp to now
                                AppPreferences.updateRecentAlertsCutoffTimestamp(DateTime.getUnixTimestamp(), Map.this);

                                // Redraw map overlays
                                redrawOverlays();

                                // Clear app notifications
                                AppNotifications.clearAll(Map.this);
                            }
                        }
                    });
                }
                else {
                    // No alerts displayed, so display all of them
                    AppPreferences.updateRecentAlertsCutoffTimestamp(0, Map.this);

                    // Redraw map overlays
                    redrawOverlays();
                }

                // Consume event
                return true;
            }
        });

        // No alerts?
        if (mDisplayAlerts.size() == 0) {
            // By default, hide button until alerts are returned by the server
            mClearRecentAlertsItem.setVisible(false);
        }
    }

    private String getShareMessage() {
        // Live map mode or no alerts?
        if (mLiveMap || mAlerts.size() == 0) {
            // Return generic app share message
            return getString(R.string.shareMessage);
        }

        // Nearby cities display?
        if (mAlerts.size() > 0 && mAlerts.get(0).threat.equals(ThreatTypes.NEARBY_CITIES_DISPLAY)) {
            // Return generic app share message
            return getString(R.string.shareMessage);
        }

        // Get city name or cities if grouped
        String cities = mAlerts.get(0).localizedCity;

        // Remove HTML entities (<b>)
        cities = HtmlCompat.fromHtml(cities, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();

        // Construct share message
        return mAlerts.get(0).localizedThreat + " " + getString(R.string.alertSoundedAt) + cities + "\n" + mAlerts.get(0).dateString + "\n\n" + getString(R.string.alertSentVia);
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

        // Save state
        mIsResumed = true;

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);

        // Clear notifications and stop sound from playing
        AppNotifications.clearAll(this);

        // Register for broadcasts
        Broadcasts.subscribe(this, mBroadcastListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save state
        mIsResumed = false;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI() {
        // Initialize display alerts list
        mDisplayAlerts = new ArrayList<>();

        // Ensure the right language is displayed
        Localization.overridePhoneLocale(this);

        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // RTL action bar hack
        RTLSupport.mirrorActionBar(this);

        // Set up UI
        setContentView(R.layout.alert_view);

        // Store reference to app icon & map cover for later
        mAppIcon = (ImageView) findViewById(R.id.appIcon);
        mMapCover = (RelativeLayout) findViewById(R.id.mapCover);

        // Align app icon based on whether language is RTL to avoid hiding Google logo
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAppIcon.getLayoutParams();
        params.addRule(Localization.isRTLLocale(this) ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
        mAppIcon.setLayoutParams(params);

        // Get map instance
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                // Map ready flag
                mIsMapReady = true;

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

    @Override
    public void onMapsSdkInitialized(MapsInitializer.Renderer renderer) {
        // Do nothing
    }

    public class LoadPolygonData extends AsyncTaskAdapter<Integer, String, Integer> {
        public LoadPolygonData() {
            // Null check
            if (mLoadingItem != null) {
                // Show loading indicator
                mLoadingItem.setVisible(true);
            }
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
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // Add city polygons and markers
            redrawOverlays();

            // Null check
            if (mLoadingItem != null) {
                // Hide loading indicator
                mLoadingItem.setVisible(false);
            }

            // Null check
            if (mShareItem != null) {
                // Show share button
                mShareItem.setVisible(true);
            }

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

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(MainActivityParameters.RELOAD_RECENT_ALERTS)) {
                // Live map?
                if (mLiveMap) {
                    reloadRecentAlerts();
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    void pollRecentAlerts() {
        // Schedule a new timer
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // App is running?
                        if (mIsResumed) {
                            // Reload every X seconds
                            reloadRecentAlerts();
                        }
                    }
                });
            }
        }, 1000 * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC, 1000 * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC);
    }

    void reloadRecentAlerts() {
        // Not already reloading?
        if (!mIsReloading) {
            // Get recent alerts async
            new GetRecentAlertsAsync().execute();
        }
    }

    public class GetRecentAlertsAsync extends AsyncTaskAdapter<Integer, String, Integer> {
        public GetRecentAlertsAsync() {
            // Prevent concurrent reload
            mIsReloading = true;

            // Null check
            if (mLoadingItem != null) {
                // Show loading indicator
                mLoadingItem.setVisible(true);
            }

            // Null check
            if (mShareItem != null) {
                // Hide share button
                mShareItem.setVisible(false);
            }
        }

        @Override
        protected Integer doInBackground(Integer... Parameter) {
            // Try to get recent alerts
            return getRecentAlerts();
        }

        @Override
        protected void onPostExecute(Integer errorStringResource) {
            // No longer reloading
            mIsReloading = false;

            // Activity dead?
            if (isFinishing() || isDestroyed()) {
                return;
            }

            // Hide loading
            mLoadingItem.setVisible(false);

            // Success?
            if (errorStringResource == 0) {
                // Redraw map overlays
                redrawOverlays();

                // Show share button
                mShareItem.setVisible(true);

                // Prevent white flicker as map loads in dark mode
                mMapCover.setVisibility(View.GONE);
            }
            else {
                // Show error toast
                Toast.makeText(Map.this, getString(errorStringResource), Toast.LENGTH_LONG).show();
            }
        }
    }

    private int getRecentAlerts() {
        // Ensure the right language is displayed
        Localization.overridePhoneLocale(this);

        // Store JSON as string initially
        String alertsJSON;

        try {
            // Get it from /alerts
            alertsJSON = HTTP.get("/alerts");
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.apiRequestFailed;
        }

        // Prepare tmp object list
        List<Alert> recentAlerts;

        try {
            // Convert JSON to object
            recentAlerts = Singleton.getJackson().readValue(alertsJSON, new TypeReference<List<Alert>>() {});
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.jsonFailed;
        }

        // Loop over alerts
        for (Alert alert : recentAlerts) {
            // Prepare string with relative time ago and fixed HH:mm:ss
            alert.dateString = LocationData.getAlertDateTimeString(alert.date, 0, this);

            // Prepare localized zone & countdown for display
            alert.desc = LocationData.getLocalizedZoneWithCountdown(alert.city, alert.threat, this);

            // Localize it
            alert.localizedCity = LocationData.getLocalizedCityName(alert.city, this);
            alert.localizedZone = LocationData.getLocalizedZoneByCityName(alert.city, this);
        }

        // Clear global list
        mAlerts.clear();

        // Add all the new alerts
        mAlerts.addAll(recentAlerts);

        // Success
        return 0;
    }

    void updateClearAlertsButton(long cutoffTimestamp) {
        // Do nothing if not in live map mode
        if (!mLiveMap) {
            return;
        }

        // Null pointer check
        if (mClearRecentAlertsItem == null) {
            return;
        }

        // By default, show clear button
        mClearRecentAlertsItem.setVisible(true);

        // In case no alerts are being displayed but alerts were returned
        if (cutoffTimestamp > 0 && mAlerts.size() > 0 && mDisplayAlerts.size() == 0) {
            // Show restore icon to allow user to restore all recent alerts
            mClearRecentAlertsItem.setIcon(R.drawable.ic_restore);
        }
        // In case there are no alerts in the past 24 hours
        else if (mAlerts.size() == 0) {
            // Hide clear button
            mClearRecentAlertsItem.setVisible(false);
        }
        else {
            // There are alerts, show default clear icon
            mClearRecentAlertsItem.setIcon(R.drawable.ic_clear);
        }
    }
}
