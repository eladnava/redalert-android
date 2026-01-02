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
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

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
    View mMapCover;
    long mReloadStartTime;
    long mInitialLoadCompleteTime;
    Timer mPollingTimer;

    // Static flag to remember if map was already loaded once (persists across tab switches)
    private static boolean sMapLoadedOnce = false;

    // Singleton alerts
    public static List<Alert> mAlerts = new ArrayList<Alert>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Using MenuProvider API instead of deprecated setHasOptionsMenu

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
        // Record when view was created for loading indicator grace period
        mInitialLoadCompleteTime = System.currentTimeMillis();
        initializeUI(view);
        setupMenu();
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

                // Fade out the loading cover
                if (mMapCover != null) {
                    mMapCover.clearAnimation(); // Stop shimmer
                    mMapCover.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .withEndAction(() -> mMapCover.setVisibility(android.view.View.GONE))
                        .start();
                }

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

    // Start shimmer/pulse animation on map cover while loading
    private void startShimmerAnimation() {
        if (mMapCover == null) return;
        
        // Create a pulsing alpha animation for a subtle shimmer effect
        android.view.animation.AlphaAnimation shimmer = new android.view.animation.AlphaAnimation(0.4f, 0.7f);
        shimmer.setDuration(800);
        shimmer.setRepeatCount(android.view.animation.Animation.INFINITE);
        shimmer.setRepeatMode(android.view.animation.Animation.REVERSE);
        shimmer.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        mMapCover.startAnimation(shimmer);
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

    void setupMenu() {
        // Menu is now managed by the Activity - share and loading items are in menu_main.xml
        // Loading indicator reference will be obtained when needed from the Activity menu
    }
    
    // Called by Activity when share button is clicked
    public void handleShareClick() {
        // Prepare share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);

        // Set as text/plain
        shareIntent.setType("text/plain");

        // Add text
        shareIntent.putExtra(Intent.EXTRA_TEXT, getShareMessage());

        // Show chooser
        startActivity(Intent.createChooser(shareIntent, getString(R.string.shareAlert)));
    }

    private String getShareMessage() {
        // Live map mode or no alerts?
        if (mLiveMap || mAlerts == null || mAlerts.size() == 0) {
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
        
        // Null check for localizedCity
        if (cities == null || cities.isEmpty()) {
            return getString(R.string.shareMessage);
        }

        // Remove HTML entities (<b>)
        cities = HtmlCompat.fromHtml(cities, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();

        // Construct share message
        return mAlerts.get(0).localizedThreat + " " + getString(R.string.alertSoundedAt) + cities + "\n"
                + mAlerts.get(0).dateString + "\n\n" + getString(R.string.alertSentVia);
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

        // Hide loading indicator immediately when leaving tab
        updateLoadingIndicator(false, false);

        // Unregister for broadcasts
        Broadcasts.unsubscribe(getContext(), mBroadcastListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop polling
        stopPollingTimer();
    }

    void initializeUI(View view) {
        // Initialize display alerts list
        mDisplayAlerts = new ArrayList<>();

        // Store reference to app icon & map cover for later
        mAppIcon = (ImageView) view.findViewById(R.id.appIcon);
        mMapCover = view.findViewById(R.id.mapCover);

        // Start shimmer animation on map cover only on first-ever load
        if (mMapCover != null) {
            if (sMapLoadedOnce) {
                // Map already loaded once this session - hide cover immediately
                mMapCover.setVisibility(View.GONE);
            } else {
                // First load - show shimmer animation
                startShimmerAnimation();
            }
        }

        // Align app icon based on whether language is RTL to avoid hiding Google logo
        if (getContext() != null) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAppIcon.getLayoutParams();
            params.addRule(Localization.isRTLLocale(getContext()) ? RelativeLayout.ALIGN_PARENT_LEFT
                    : RelativeLayout.ALIGN_PARENT_RIGHT);
            mAppIcon.setLayoutParams(params);
        }

        // Add SupportMapFragment programmatically
        // On first load, use delay to let tab animation complete before heavy map init
        // On subsequent loads, create immediately for instant switching
        Runnable createMapFragment = () -> {
            if (getContext() == null || !isAdded()) return;
            
            // Set initial camera position to Israel (same as was in XML attributes)
            com.google.android.gms.maps.GoogleMapOptions options = new com.google.android.gms.maps.GoogleMapOptions()
                .camera(new com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(new LatLng(31.401331884548895, 34.84646325081433)) // Israel center
                    .zoom(7.8f)
                    .build());
            
            SupportMapFragment mapFragment = SupportMapFragment.newInstance(options);
            getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commitAllowingStateLoss();
            
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;

                    // Map ready flag
                    mIsMapReady = true;

                    // Initialize map
                    initializeMap();
                }
            });
        };
        
        if (sMapLoadedOnce) {
            // Map already loaded once - create immediately
            createMapFragment.run();
        } else {
            // First load - delay to let tab switch animation complete
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(createMapFragment, 300);
        }
    }

    // Show/hide loading indicator via Activity's menu
    private void updateLoadingIndicator(boolean show, boolean animate) {
        if (getActivity() == null) return;
        
        // Access the Activity's menu through the toolbar
        if (getActivity() instanceof com.red.alert.activities.Main) {
            com.red.alert.activities.Main mainActivity = (com.red.alert.activities.Main) getActivity();
            mainActivity.setMapLoadingVisible(show, animate);
        }
    }

    @Override
    public void onMapsSdkInitialized(MapsInitializer.Renderer renderer) {
        // Do nothing
    }

    public class LoadPolygonData extends AsyncTaskAdapter<Integer, String, Integer> {
        public LoadPolygonData() {
            // Only show loading indicator after initial grace period has passed
            boolean pastInitialLoadGracePeriod = mInitialLoadCompleteTime > 0 && 
                (System.currentTimeMillis() - mInitialLoadCompleteTime) > 5000;
            if (pastInitialLoadGracePeriod) {
                // Show loading indicator via Activity
                updateLoadingIndicator(true, false);
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

            // Hide loading indicator
            updateLoadingIndicator(false, false);

            // Delay by 200ms
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Fade out shimmer cover smoothly
                    if (mMapCover != null && mMapCover.getVisibility() == View.VISIBLE) {
                        // Clear any running shimmer animation first
                        mMapCover.clearAnimation();
                        // Fade out over 300ms
                        mMapCover.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(() -> {
                                mMapCover.setVisibility(View.GONE);
                                mMapCover.setAlpha(1f); // Reset alpha for potential future use
                                // Remember that map has loaded once
                                sMapLoadedOnce = true;
                            })
                            .start();
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
        
        // Schedule a new timer - initial delay 0 for immediate first load
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
        }, 0, // Start immediately
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
            
            // Record start time for minimum display duration
            mReloadStartTime = System.currentTimeMillis();

            // Show loading indicator in action bar with fade-in 
            // Only show after at least 5 seconds have passed since view creation
            // This prevents showing it during the initial load and camera animations
            boolean pastInitialLoadGracePeriod = mInitialLoadCompleteTime > 0 && 
                (System.currentTimeMillis() - mInitialLoadCompleteTime) > 5000;
            if (mIsResumed && pastInitialLoadGracePeriod) {
                updateLoadingIndicator(true, true);
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

            // Calculate how long loading took
            long elapsed = System.currentTimeMillis() - mReloadStartTime;
            long minimumDisplayTime = 1000; // 1 second minimum
            long delayBeforeHide = Math.max(0, minimumDisplayTime - elapsed);
            
            // Hide loading indicator with fade-out after minimum display time
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (getContext() != null) {
                    updateLoadingIndicator(false, true);
                }
            }, delayBeforeHide);

            // Success?
            if (errorStringResource == 0) {
                // Redraw map overlays
                redrawOverlays();

                // Fade out shimmer cover smoothly if visible
                if (mMapCover != null && mMapCover.getVisibility() == View.VISIBLE) {
                    mMapCover.clearAnimation();
                    mMapCover.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .withEndAction(() -> {
                            mMapCover.setVisibility(View.GONE);
                            mMapCover.setAlpha(1f);
                            sMapLoadedOnce = true;
                        })
                        .start();
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
