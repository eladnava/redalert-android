package com.red.alert.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;

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
import com.red.alert.activities.Main;
import com.red.alert.config.RecentAlerts;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.Alert;
import com.red.alert.model.metadata.City;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.localization.rtl.adapters.RTLMarkerInfoWindowAdapter;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AlertsMapFragment extends Fragment implements OnMapsSdkInitializedCallback {
    GoogleMap mMap;

    List<Alert> mDisplayAlerts;

    boolean mLiveMap = true; // Always live map in fragment
    boolean mIsMapReady;
    boolean mIsResumed;
    boolean mIsReloading;

    int mDisplayedAlertsCount;

    ImageView mAppIcon;
    MenuItem mShareItem;
    MenuItem mLoadingItem;
    RelativeLayout mMapCover;
    Timer mPollingTimer;

    // Singleton alerts
    public static List<Alert> mAlerts = new ArrayList<Alert>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // Use legacy maps renderer to fix blank map bug for 1% of users
        useLegacyRenderer();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alerts_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeUI(view);
    }

    void useLegacyRenderer() {
        // Use background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Use legacy maps renderer
                if (getContext() != null) {
                    MapsInitializer.initialize(getContext(), MapsInitializer.Renderer.LEGACY, AlertsMapFragment.this);
                }
            }
        }).start();
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
                if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }

                // Load polygon data async
                new LoadPolygonData().execute();
            }
        });

        // Live map?
        if (mLiveMap) {
            pollRecentAlerts();
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
                    getContext(), R.raw.map_style_dark));
        }

        // Wait for map to load
        mapLoadedListener();
    }

    void redrawOverlays() {
        // Workaround for ConcurrentModificationException
        // Wait for reloading to complete
        if (mIsReloading || !mIsMapReady || getContext() == null) {
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
        HashMap<String, ArrayList<ArrayList<Double>>> polygons = LocationData.getAllPolygons(getContext());

        // List of processed cities
        List<String> cityNames = new ArrayList<String>();

        // Check if user cleared the recent alerts
        long cutoffTimestamp = AppPreferences.getRecentAlertsCutoffTimestamp(getContext());

        // Traverse alerts
        for (Alert alert : mAlerts) {
            // Remove alerts that occurred before the cutoff
            if (mLiveMap && cutoffTimestamp > 0 && alert.date <= cutoffTimestamp) {
                continue;
            }

            // Get city object
            City city = LocationData.getCityByName(alert.city, getContext());

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
                String localizedName = LocationData.getLocalizedCityName(city.name, getContext()) + " ("
                        + LocationData.getLocalizedZoneByCityName(city.name, getContext()) + ")";

                // Create LatLng location object
                LatLng location = new LatLng(city.latitude, city.longitude);

                // Marker tooltip
                String tooltip = LocationData.getAlertDateTimeString(alert.date, 0, getContext());

                // Add shelter count if exists for this city
                if (city.shelters > 0) {
                    tooltip += "\n" + getString(R.string.lifeshieldShelters) + " " + city.shelters;
                }

                // Custom tooltip for nearby cities display
                if (alert.threat.equals(ThreatTypes.NEARBY_CITIES_DISPLAY)) {
                    // Display current distance from city center
                    tooltip = LocationData.getDistanceFromCity(city, getContext()) + " "
                            + getString(R.string.kilometer);
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
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1500,
                            new GoogleMap.CancelableCallback() {
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
    public void onCreateOptionsMenu(@NonNull Menu OptionsMenu, @NonNull MenuInflater inflater) {
        // Add share button
        initializeShareButton(OptionsMenu);

        // Add loading indicator
        initializeLoadingIndicator(OptionsMenu);

        super.onCreateOptionsMenu(OptionsMenu, inflater);
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
        return mAlerts.get(0).localizedThreat + " " + getString(R.string.alertSoundedAt) + cities + "\n"
                + mAlerts.get(0).dateString + "\n\n" + getString(R.string.alertSentVia);
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add share icon to Action Bar
        mShareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.shareAlert));

        // Set share icon
        mShareItem.setIcon(R.drawable.ic_share_outline);

        // Hide by default
        mShareItem.setVisible(true);

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

    @Override
    public void onResume() {
        super.onResume();

        // Save state
        mIsResumed = true;

        // Clear notifications and stop sound from playing
        if (getContext() != null) {
            AppNotifications.clearAll(getContext());
        }

        // Register for broadcasts
        Broadcasts.subscribe(getContext(), mBroadcastListener);

        // Reset UI to root state (title, back arrow, etc.)
        if (getActivity() instanceof com.red.alert.activities.Main) {
            ((com.red.alert.activities.Main) getActivity()).resetToRoot(getString(R.string.alerts), false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save state
        mIsResumed = false;

        // Stop polling timer to prevent memory leak
        stopPollingTimer();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(getContext(), mBroadcastListener);
    }

    void initializeUI(View view) {
        // Initialize display alerts list
        mDisplayAlerts = new ArrayList<>();

        // Store reference to app icon & map cover for later
        mAppIcon = (ImageView) view.findViewById(R.id.appIcon);
        mMapCover = (RelativeLayout) view.findViewById(R.id.mapCover);

        // Align app icon based on whether language is RTL to avoid hiding Google logo
        if (getContext() != null) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAppIcon.getLayoutParams();
            params.addRule(Localization.isRTLLocale(getContext()) ? RelativeLayout.ALIGN_PARENT_LEFT
                    : RelativeLayout.ALIGN_PARENT_RIGHT);
            mAppIcon.setLayoutParams(params);
        }

        // Get map instance
        ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map))
                .getMapAsync(new OnMapReadyCallback() {
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
                // mLoadingItem.setVisible(true);
            }
        }

        @Override
        protected Integer doInBackground(Integer... Parameter) {
            // Load polygons data for all cities (execution takes roughly 1 second)
            if (getContext() != null) {
                LocationData.getAllPolygons(getContext());
            }

            // No errors
            return 0;
        }

        @Override
        protected void onPostExecute(Integer error) {
            // Activity dead?
            if (getContext() == null) {
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
                    if (mMapCover != null) {
                        mMapCover.setVisibility(View.GONE);
                    }
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

    void pollRecentAlerts() {
        // Cancel existing timer if any
        if (mPollingTimer != null) {
            mPollingTimer.cancel();
        }
        
        // Schedule a new timer
        mPollingTimer = new Timer();
        mPollingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
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
            }
        }, 1000 * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC,
                1000 * RecentAlerts.RECENT_ALERTS_POLLING_INTERVAL_SEC);
    }
    
    void stopPollingTimer() {
        if (mPollingTimer != null) {
            mPollingTimer.cancel();
            mPollingTimer = null;
        }
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
                // mLoadingItem.setVisible(true);
            }

            // Null check
            if (mShareItem != null) {
                // Hide share button
                // mShareItem.setVisible(false);
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
            if (getContext() == null) {
                return;
            }

            // Hide loading
            if (mLoadingItem != null) {
                mLoadingItem.setVisible(false);
            }

            // Success?
            if (errorStringResource == 0) {
                // Redraw map overlays
                redrawOverlays();

                // Show share button
                if (mShareItem != null) {
                    mShareItem.setVisible(true);
                }

                // Prevent white flicker as map loads in dark mode
                if (mMapCover != null) {
                    mMapCover.setVisibility(View.GONE);
                }
            } else {
                // Show error toast
                Toast.makeText(getContext(), getString(errorStringResource), Toast.LENGTH_LONG).show();
            }
        }
    }

    private int getRecentAlerts() {
        // Ensure the right language is displayed
        if (getContext() != null) {
            Localization.overridePhoneLocale(getContext());
        }

        // Store JSON as string initially
        String alertsJSON;

        try {
            // Get it from /alerts
            alertsJSON = com.red.alert.utils.networking.HTTP.get("/alerts");
        } catch (Exception exc) {
            // Log it
            android.util.Log.e(com.red.alert.config.Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.apiRequestFailed;
        }

        // Prepare tmp object list
        List<Alert> recentAlerts;

        try {
            // Convert JSON to object
            recentAlerts = com.red.alert.utils.caching.Singleton.getJackson().readValue(alertsJSON,
                    new me.pushy.sdk.lib.jackson.core.type.TypeReference<List<Alert>>() {
                    });
        } catch (Exception exc) {
            // Log it
            android.util.Log.e(com.red.alert.config.Logging.TAG, "Get recent alerts request failed", exc);

            // Return error code
            return R.string.jsonFailed;
        }

        // Loop over alerts
        for (Alert alert : recentAlerts) {
            // Prepare string with relative time ago and fixed HH:mm:ss
            if (getContext() != null) {
                alert.dateString = LocationData.getAlertDateTimeString(alert.date, 0, getContext());

                // Prepare localized zone & countdown for display
                alert.desc = LocationData.getLocalizedZoneWithCountdown(alert.city, alert.threat, getContext());

                // Localize it
                alert.localizedCity = LocationData.getLocalizedCityName(alert.city, getContext());
                alert.localizedZone = LocationData.getLocalizedZoneByCityName(alert.city, getContext());
            }
        }

        // Clear global list
        mAlerts.clear();

        // Add all the new alerts
        mAlerts.addAll(recentAlerts);

        // Success
        return 0;
    }

}
