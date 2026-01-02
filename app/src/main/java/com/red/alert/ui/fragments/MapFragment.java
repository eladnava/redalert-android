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
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.BackEventCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.red.alert.R;
import com.red.alert.config.RecentAlerts;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.Alert;
import com.red.alert.model.metadata.City;
import com.red.alert.ui.localization.rtl.RTLSupport;
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

public class MapFragment extends Fragment {
    GoogleMap mMap;

    List<Alert> mDisplayAlerts;

    boolean mLiveMap;
    boolean mIsMapReady;
    boolean mIsResumed;
    boolean mIsReloading;

    int mDisplayedAlertsCount;

    ImageView mAppIcon;
    RelativeLayout mMapCover;
    MenuItem mClearRecentAlertsItem;
    Timer mPollingTimer;

    // Menu items
    MenuItem mShareItem;
    MenuItem mLoadingItem;

    // Instance alerts list - NOT static since each fragment instance should have its own alerts
    private List<Alert> mAlerts = new ArrayList<Alert>();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            // Max corner radius in pixels (Standard Material 3 is 28dp, but we can go
            // larger for effect)
            final float maxCornerRadius = android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
            
            // Track current corner radius for seamless container transform
            float currentCornerRadius = 0f;

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (getView() != null) {
                    // Scale down to 90% at max swipe
                    float scale = 1f - (backEvent.getProgress() * 0.1f);
                    getView().setScaleX(scale);
                    getView().setScaleY(scale);

                    // Animate corner radius and track it
                    currentCornerRadius = maxCornerRadius * backEvent.getProgress();
                    getView().setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), currentCornerRadius);
                        }
                    });
                    getView().setClipToOutline(true);
                }
            }

            @Override
            public void handleOnBackCancelled() {
                if (getView() != null) {
                    // Animate back to full size
                    getView().animate().scaleX(1f).scaleY(1f).setDuration(200).start();

                    // Reset corner radius
                    currentCornerRadius = 0f;
                    getView().setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 0);
                        }
                    });
                    getView().setClipToOutline(true);
                }
            }

            @Override
            public void handleOnBackPressed() {
                // Update return transition's start shape to match current predictive back corners
                if (currentCornerRadius > 0 && getSharedElementReturnTransition() instanceof 
                        com.google.android.material.transition.MaterialContainerTransform) {
                    com.google.android.material.transition.MaterialContainerTransform returnTransition = 
                        (com.google.android.material.transition.MaterialContainerTransform) getSharedElementReturnTransition();
                    returnTransition.setStartShapeAppearanceModel(
                        new com.google.android.material.shape.ShapeAppearanceModel.Builder()
                            .setAllCornerSizes(currentCornerRadius)
                            .build());
                }
                
                // Show cover immediately to hide map during return transition
                if (mMapCover != null) {
                    mMapCover.setAlpha(1f);
                    mMapCover.setVisibility(View.VISIBLE);
                    
                    // Use post to ensure cover is rendered before triggering back
                    mMapCover.post(() -> {
                        setEnabled(false);
                        requireActivity().onBackPressed();
                    });
                } else {
                    setEnabled(false);
                    requireActivity().onBackPressed();
                }
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use legacy maps renderer to fix blank map bug for 1% of users
        useLegacyRenderer();

        // Initialize alert
        unpackArguments();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.alert_view, container, false);

        // Set transition name if passed
        Bundle args = getArguments();
        if (args != null && args.containsKey("transition_name")) {
            view.setTransitionName(args.getString("transition_name"));
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI
        initializeUI(view);

        // Support for RTL languages (after toolbar is set up)
        RTLSupport.mirrorActionBar(requireActivity());

        // Setup menu
        setupMenu();

        // Postpone enter transition
        postponeEnterTransition();
        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                // Add share button only - loading indicator and clear button removed
                // to prevent action bar menu jump issues
                initializeShareButton(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == android.R.id.home) {
                    requireActivity().onBackPressed();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.STARTED);
    }

    void useLegacyRenderer() {
        // Use background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Use legacy maps renderer
                if (getContext() != null) {
                    MapsInitializer.initialize(getContext(), MapsInitializer.Renderer.LEGACY, null);
                }
            }
        }).start();
    }

    void unpackArguments() {
        // Get arguments
        Bundle args = getArguments();
        if (args != null) {
            mLiveMap = args.getBoolean(AlertViewParameters.LIVE, false);

            // Check if alerts were passed
            String alertsJson = args.getString("alerts_json");
            if (alertsJson != null) {
                try {
                    // Deserialize alerts
                    mAlerts = com.red.alert.utils.caching.Singleton.getJackson().readValue(alertsJson,
                            new me.pushy.sdk.lib.jackson.core.type.TypeReference<List<Alert>>() {
                            });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void mapLoadedListener() {
        if (mMap == null)
            return;

        // Wait for map to load
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition arg0) {
                // Prevent from being called again
                if (mMap != null) {
                    mMap.setOnCameraChangeListener(null);

                    // Fix RTL bug
                    mMap.setInfoWindowAdapter(new RTLMarkerInfoWindowAdapter(getLayoutInflater()));

                    // Show my location button
                    if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                    }

                    // Set activity title
                    setActivityTitle();

                    // Load polygon data async
                    new LoadPolygonData().execute();
                }
            }
        });

        // Live map?
        if (mLiveMap) {
            pollRecentAlerts();
        }
    }

    void setActivityTitle() {
        if (!isAdded())
            return;

        // Live map?
        if (mLiveMap) {
            // Set recent alerts title
            requireActivity().setTitle(getString(R.string.recentAlerts));
        }

        // Have at least one alert?
        else if (mAlerts.size() > 0) {
            // Get first alert object
            Alert firstAlert = mAlerts.get(0);

            // Set title to alert relative time ago
            requireActivity().setTitle(LocationData.getAlertRelativeTimeAgo(firstAlert.date, getContext()));

            // Add localized threat type to title
            if (!StringUtils.stringIsNullOrEmpty(firstAlert.localizedThreat)) {
                requireActivity().setTitle(firstAlert.localizedThreat + ": " + requireActivity().getTitle());
            }

            // Display special title for nearby cities display
            if (firstAlert.threat.equals(ThreatTypes.NEARBY_CITIES_DISPLAY)) {
                requireActivity().setTitle(getString(R.string.nearbyCities) + ": "
                        + LocationLogic.getMaxDistanceKilometers(getContext(), -1)
                        + " " + getString(R.string.kilometer));
            }
        } else {
            // Nearby cities display and no nearby cities
            requireActivity().setTitle(getString(R.string.noNearbyCities));
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
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Animate nicely
                    if (mMap != null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1500,
                                new GoogleMap.CancelableCallback() {
                                    @Override
                                    public void onFinish() {
                                        // Allow user to zoom in freely
                                        if (mMap != null) {
                                            mMap.resetMinMaxZoomPreference();
                                        }
                                    }

                                    @Override
                                    public void onCancel() {
                                    }
                                });
                    }
                } catch (Exception exc) {
                    // Ignore rare exception "View size is too small after padding is applied"
                }
            }
        }, 500);
    }

    void initializeClearRecentAlertsButton(Menu OptionsMenu) {
        // Only display button in live map mode
        if (!mLiveMap) {
            return;
        }

        // Add clear item
        mClearRecentAlertsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE,
                getString(R.string.clearRecentAlerts));

        // Set default icon
        mClearRecentAlertsItem.setIcon(R.drawable.ic_close_outline);

        // Specify the show flags
        mClearRecentAlertsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, toggle clearing recent alerts
        mClearRecentAlertsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Clear currently displayed alerts
                if (mDisplayAlerts.size() > 0) {
                    // Show a dialog to the user
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.clearRecentAlerts)
                            .setMessage(R.string.clearRecentAlertsDesc)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Set recent alerts cutoff timestamp to now
                                    AppPreferences.updateRecentAlertsCutoffTimestamp(DateTime.getUnixTimestamp(),
                                            getContext());

                                    // Redraw map overlays
                                    redrawOverlays();

                                    // Clear app notifications
                                    AppNotifications.clearAll(getContext());
                                }
                            })
                            .setNegativeButton(R.string.no, null)
                            .show();
                } else {
                    // No alerts displayed, so display all of them
                    AppPreferences.updateRecentAlertsCutoffTimestamp(0, getContext());

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

    void updateClearAlertsButton(long cutoffTimestamp) {
        if (mClearRecentAlertsItem == null)
            return;

        // No alerts?
        if (mDisplayAlerts.size() == 0) {
            // Hide button
            mClearRecentAlertsItem.setVisible(false);
            return;
        }

        // Show button
        mClearRecentAlertsItem.setVisible(true);

        // Check if user cleared the recent alerts
        if (cutoffTimestamp > 0) {
            // Show "show all" icon
            mClearRecentAlertsItem.setIcon(R.drawable.ic_restore_outline);
            mClearRecentAlertsItem.setTitle(R.string.recentAlerts);
        } else {
            // Show "clear" icon
            mClearRecentAlertsItem.setIcon(R.drawable.ic_close_outline);
            mClearRecentAlertsItem.setTitle(R.string.clearRecentAlerts);
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
        String threat = mAlerts.get(0).localizedThreat;
        String dateStr = mAlerts.get(0).dateString;

        // Null check for required fields - fall back to generic message if missing
        if (cities == null || cities.isEmpty() || threat == null || threat.isEmpty()) {
            return getString(R.string.shareMessage);
        }

        // Remove HTML entities (<b>)
        cities = HtmlCompat.fromHtml(cities, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();

        // Construct share message
        return threat + " " + getString(R.string.alertSoundedAt) + cities + "\n"
                + (dateStr != null ? dateStr : "") + "\n\n" + getString(R.string.alertSentVia);
    }

    void initializeShareButton(Menu OptionsMenu) {
        // Add share icon to Action Bar
        mShareItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.shareAlert));

        // Set share icon
        mShareItem.setIcon(R.drawable.ic_share_outline);

        // For individual alert view (non-live map), show share button immediately
        // For live map mode, keep hidden until loading completes
        mShareItem.setVisible(!mLiveMap);

        // Specify the show flags
        mShareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

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

        // Support for RTL languages
        if (getActivity() != null) {
            RTLSupport.mirrorActionBar(getActivity());
        }

        // Clear notifications and stop sound from playing
        AppNotifications.clearAll(getContext());

        // Register for broadcasts
        Broadcasts.subscribe(getContext(), mBroadcastListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save state
        mIsResumed = false;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages (after toolbar is set up)
        if (getActivity() != null) {
            RTLSupport.mirrorActionBar(getActivity());
        }
    }

    void initializeUI(View view) {
        // Initialize display alerts list
        mDisplayAlerts = new ArrayList<>();

        // Allow click on home button
        if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Store reference to app icon & map cover for later
        mAppIcon = (ImageView) view.findViewById(R.id.appIcon);
        mMapCover = (RelativeLayout) view.findViewById(R.id.mapCover);

        // Align app icon based on whether language is RTL to avoid hiding Google logo
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mAppIcon.getLayoutParams();
        params.addRule(
                Localization.isRTLLocale(getContext()) ? RelativeLayout.ALIGN_PARENT_LEFT
                        : RelativeLayout.ALIGN_PARENT_RIGHT);
        mAppIcon.setLayoutParams(params);

        // Get map instance
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
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
        }
    }

    void initializeLoadingIndicator(Menu OptionsMenu) {
        // Add loading spinner to Action Bar
        mLoadingItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.loading));

        // Set up the view
        mLoadingItem.setActionView(R.layout.loading);

        // Specify the show flags
        mLoadingItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Hide by default
        mLoadingItem.setVisible(false);
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
            if (getContext() != null) {
                LocationData.getAllPolygons(getContext());
            }

            // No errors
            return 0;
        }

        @Override
        protected void onPostExecute(Integer error) {
            // Fragment dead?
            if (!isAdded()) {
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
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop polling timer to prevent memory leak
        stopPollingTimer();

        // Unregister for broadcasts
        Broadcasts.unsubscribe(getContext(), mBroadcastListener);
    }

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
                if (getActivity() == null)
                    return;

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

            // Fragment dead?
            if (!isAdded()) {
                return;
            }

            // Success?
            if (errorStringResource == 0) {
                // Redraw map
                redrawOverlays();
            }

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
            alert.dateString = LocationData.getAlertDateTimeString(alert.date, 0, getContext());

            // Prepare localized zone & countdown for display
            alert.desc = LocationData.getLocalizedZoneWithCountdown(alert.city, alert.threat, getContext());

            // Localize it
            alert.localizedCity = LocationData.getLocalizedCityName(alert.city, getContext());
            alert.localizedZone = LocationData.getLocalizedZoneByCityName(alert.city, getContext());
        }

        // Clear global list
        mAlerts.clear();

        // Add all the new alerts
        mAlerts.addAll(recentAlerts);

        // Success
        return 0;
    }
}
