package com.red.alert.ui.fragments;

import com.red.alert.ui.fragments.MapFragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.red.alert.R;
import com.red.alert.activities.Main;
import com.red.alert.activities.Map;
import com.red.alert.config.Logging;
import com.red.alert.config.RecentAlerts;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.communication.intents.MainActivityParameters;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.Alert;
import com.red.alert.ui.adapters.AlertAdapter;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.content.DialogInterface;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import me.pushy.sdk.lib.jackson.core.type.TypeReference;

public class AlertsFragment extends Fragment {
    RecyclerView mAlertsList;
    LinearProgressIndicator mLoading;
    LinearLayout mNoAlerts;
    AlertAdapter mAlertsAdapter;
    SwipeRefreshLayout mSwipeRefresh;
    View mSkeletonLoading;

    boolean mIsResumed;
    boolean mIsReloading;
    boolean mHasLoaded;
    int mPreviousAlertCount = 0;
    Timer mPollingTimer;

    List<Alert> mNewAlerts;
    List<Alert> mDisplayAlerts;





    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Asked for reload?
            if (Key.equalsIgnoreCase(MainActivityParameters.RELOAD_RECENT_ALERTS)) {
                reloadRecentAlerts();
            }
        }
    };

    // Static cache to prevent reloading on tab switch
    private static List<Alert> sCachedAlerts;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Using MenuProvider API instead of deprecated setHasOptionsMenu
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Inflate layout
        return inflater.inflate(R.layout.fragment_alerts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI
        initializeUI(view);

        // Menu is now handled at Activity level (Main.java) to avoid flicker
        // from MenuProvider recreation on lifecycle changes

        // Start polling
        // Start polling
        // pollRecentAlerts(); // Moved to onResume
    }

    void initializeUI(View view) {
        // Get views
        mAlertsList = view.findViewById(R.id.alerts);
        mLoading = view.findViewById(R.id.loading);
        mNoAlerts = view.findViewById(R.id.noAlerts);
        mSwipeRefresh = view.findViewById(R.id.swipeRefresh);
        mSkeletonLoading = view.findViewById(R.id.skeleton_loading);

        // Initialize lists (if not already initialized)
        if (mNewAlerts == null) {
            mNewAlerts = new ArrayList<>();
        }
        if (mDisplayAlerts == null) {
            mDisplayAlerts = new ArrayList<>();
        }

        // Set up RecyclerView
        mAlertsList.setLayoutManager(new LinearLayoutManager(getContext()));
        mAlertsList.setHasFixedSize(true);

        // Create adapter
        mAlertsAdapter = new AlertAdapter(getActivity(), mDisplayAlerts);

        // Postpone enter transition until RecyclerView is ready
        postponeEnterTransition();
        mAlertsList.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mAlertsList.getViewTreeObserver().removeOnPreDrawListener(this);
                startPostponedEnterTransition();
                return true;
            }
        });

        // Set up click listener
        mAlertsAdapter.setOnItemClickListener(new AlertAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Alert alert, int position, View cardView) {
                // No map view for system alerts
                if (alert.threat.equals(ThreatTypes.SYSTEM)) {
                    return;
                }

                // Create MapFragment
                MapFragment mapFragment = new MapFragment();
                Bundle args = new Bundle();

                try {
                    // Serialize alerts to JSON string since Alert is not Parcelable
                    String alertsJson = com.red.alert.utils.caching.Singleton.getJackson()
                            .writeValueAsString(alert.groupedAlerts);
                    args.putString("alerts_json", alertsJson);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Get transition name from view
                String transitionName = cardView.getTransitionName();

                // Pass transition name to MapFragment
                args.putString("transition_name", transitionName);
                mapFragment.setArguments(args);

                // Set up transitions
                com.google.android.material.transition.MaterialContainerTransform enterTransition = new com.google.android.material.transition.MaterialContainerTransform();
                enterTransition.setDrawingViewId(R.id.fragment_container);
                mapFragment.setSharedElementEnterTransition(enterTransition);

                com.google.android.material.transition.MaterialContainerTransform returnTransition = new com.google.android.material.transition.MaterialContainerTransform();
                returnTransition.setDrawingViewId(android.R.id.content);
                returnTransition.setDuration(300);
                returnTransition.setFadeMode(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_CROSS);
                returnTransition.setScrimColor(android.graphics.Color.TRANSPARENT);
                // Set container color to match card background (maps SurfaceView can't be captured)
                returnTransition.setContainerColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md3_surfaceContainerHighest));
                // Set end shape with rounded corners to match card's 16dp corner radius
                float cornerRadius = getResources().getDimension(R.dimen.card_corner_radius);
                returnTransition.setEndShapeAppearanceModel(
                    new com.google.android.material.shape.ShapeAppearanceModel.Builder()
                        .setAllCornerSizes(cornerRadius)
                        .build());
                mapFragment.setSharedElementReturnTransition(returnTransition);
                mapFragment.setAllowReturnTransitionOverlap(true);

                // Set exit transition for this fragment
                setExitTransition(new com.google.android.material.transition.Hold());
                setReenterTransition(new com.google.android.material.transition.Hold());

                // Perform transaction
                getParentFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .addSharedElement(cardView, transitionName)
                        .replace(R.id.fragment_container, mapFragment)
                        .addToBackStack(null)
                        .commit();

                // Update Main Activity UI
                if (getActivity() instanceof com.red.alert.activities.Main) {
                    // Title: threat type, Subtitle: city names (for MD3 two-line toolbar)
                    String title = alert.localizedThreat;
                    String subtitle = null;
                    if (alert.localizedCity != null && !alert.localizedCity.isEmpty()) {
                        // Remove HTML tags from localizedCity for subtitle display
                        subtitle = alert.localizedCity.replaceAll("<[^>]*>", "");
                    }
                    ((com.red.alert.activities.Main) getActivity()).onMapFragmentInteraction(title, subtitle);
                }
            }
        });

        // Attach adapter
        mAlertsList.setAdapter(mAlertsAdapter);

        // Set up swipe to refresh
        mSwipeRefresh.setColorSchemeColors(ContextCompat.getColor(getContext(), R.color.md3_primary));
        mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reloadRecentAlerts();
            }
        });

        // Restore from cache if available
        if (sCachedAlerts != null && !sCachedAlerts.isEmpty()) {
            mNewAlerts.clear();
            mNewAlerts.addAll(sCachedAlerts);
            invalidateAlertList();
            mHasLoaded = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;

        // Clear any Hold transitions that were set when opening an alert
        // This restores normal fade animation behavior for tab switches
        setExitTransition(null);
        setReenterTransition(null);

        // Register broadcasts
        Broadcasts.subscribe(getContext(), mBroadcastListener);

        // Reload alerts
        // Reload alerts
        reloadRecentAlerts();

        // Start polling
        startPollingTimer();

        // Reset UI to root state (title, back arrow, etc.)
        if (getActivity() instanceof com.red.alert.activities.Main) {
            ((com.red.alert.activities.Main) getActivity()).resetToRoot(getString(R.string.recentAlerts), true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsResumed = false;

        // Stop polling
        stopPollingTimer();

        // Unregister broadcasts
        Broadcasts.unsubscribe(getContext(), mBroadcastListener);
    }



    void startPollingTimer() {
        if (mPollingTimer != null) {
            mPollingTimer.cancel();
        }
        mPollingTimer = new Timer();
        mPollingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mIsResumed) {
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

    public void reloadRecentAlerts() {
        if (!mIsReloading) {
            // Only show loading if we haven't loaded yet AND we don't have cached alerts
            boolean showLoading = !mHasLoaded && (sCachedAlerts == null || sCachedAlerts.isEmpty());

            // If NOT showing loading, ensure UI is reset (skeleton hidden, list visible)
            // This fixes the "frozen" state if we resume after a detached loading state
            if (!showLoading) {
                if (mSkeletonLoading != null) {
                    stopSkeletonAnimation();
                    mSkeletonLoading.setVisibility(View.GONE);
                }
                if (mAlertsList != null) {
                    mAlertsList.setVisibility(View.VISIBLE);
                    mAlertsList.setAlpha(1f);
                }
            }

            new GetRecentAlertsAsync(showLoading).execute();
        }
    }

    void toggleLinearProgressVisibility(boolean visibility) {
        if (mLoading == null) {
            return;
        }

        if (visibility) {
            mLoading.setVisibility(View.VISIBLE);
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(300);
            fadeIn.setFillAfter(true);
            mLoading.startAnimation(fadeIn);
        } else {
            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
            fadeOut.setDuration(300);
            fadeOut.setFillAfter(true);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    mLoading.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            mLoading.startAnimation(fadeOut);
        }
    }

    private int getRecentAlerts(android.content.Context context) {
        Localization.overridePhoneLocale(context);

        String alertsJSON;
        try {
            alertsJSON = HTTP.get("/alerts");
        } catch (Exception exc) {
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);
            return R.string.apiRequestFailed;
        }

        List<Alert> recentAlerts;
        try {
            recentAlerts = Singleton.getJackson().readValue(alertsJSON, new TypeReference<List<Alert>>() {
            });
        } catch (Exception exc) {
            Log.e(Logging.TAG, "Get recent alerts request failed", exc);
            return R.string.apiRequestFailed;
        }


        if (recentAlerts == null) {
            recentAlerts = new ArrayList<>();
        }

        for (Alert alert : recentAlerts) {
            // Skip alerts with missing required fields
            if (alert.city == null || alert.threat == null) {
                continue;
            }



            alert.dateString = LocationData.getAlertDateTimeString(alert.date, 0, context);
            alert.desc = LocationData.getLocalizedZoneWithCountdown(alert.city, alert.threat, context);
            alert.localizedCity = LocationData.getLocalizedCityName(alert.city, context);
            alert.localizedZone = LocationData.getLocalizedZoneByCityName(alert.city, context);
            alert.localizedThreat = LocationData.getLocalizedThreatType(alert.threat, context);

            if (AlertLogic.isCitySelectedPrimarily(alert.city, true, context)
                    || AlertLogic.isNearby(alert.city, context)
                    || AlertLogic.isSecondaryCitySelected(alert.city, true, context)) {
                alert.localizedCity = "<b>" + alert.localizedCity + "</b>";
            }
        }

        recentAlerts = groupAlerts(recentAlerts, context);
        mNewAlerts.clear();
        mNewAlerts.addAll(recentAlerts);
        return 0;
    }



    private List<Alert> groupAlerts(List<Alert> alerts, android.content.Context context) {
        List<Alert> groupedAlerts = new ArrayList<>();
        Alert lastAlert = null;

        for (int i = 0; i < alerts.size(); i++) {
            Alert currentAlert = alerts.get(i);
            currentAlert.groupedDescriptions = new ArrayList<>();
            currentAlert.groupedAlerts = new ArrayList<>();
            currentAlert.groupedLocalizedCities = new ArrayList<>();
            currentAlert.groupedAlerts.add(currentAlert);

            if (!StringUtils.stringIsNullOrEmpty(currentAlert.desc)) {
                currentAlert.groupedDescriptions.add(currentAlert.desc);
            }
            currentAlert.groupedLocalizedCities.add(currentAlert.localizedCity);

            if (lastAlert != null && currentAlert.date >= lastAlert.date - 15
                    && currentAlert.date <= lastAlert.date + 15) {
                lastAlert.groupedLocalizedCities.add(currentAlert.localizedCity);
                // Add null checks for desc and localizedZone
                boolean shouldAddDesc = lastAlert.desc == null || currentAlert.localizedZone == null
                        || !lastAlert.desc.contains(currentAlert.localizedZone);
                if (shouldAddDesc) {
                    if (StringUtils.stringIsNullOrEmpty(lastAlert.desc)
                            && !StringUtils.stringIsNullOrEmpty(currentAlert.desc)) {
                        lastAlert.desc = currentAlert.desc;
                        lastAlert.groupedDescriptions.add(currentAlert.desc);
                    } else if (!StringUtils.stringIsNullOrEmpty(currentAlert.desc)) {
                        lastAlert.desc += ", " + currentAlert.desc;
                        lastAlert.groupedDescriptions.add(currentAlert.desc);
                    }
                }
                lastAlert.groupedAlerts.add(currentAlert);
                if (lastAlert.date != currentAlert.date) {
                    lastAlert.dateString = LocationData.getAlertDateTimeString(lastAlert.date, currentAlert.date,
                            context);
                }
            } else {
                groupedAlerts.add(currentAlert);
                lastAlert = currentAlert;
            }
        }

        for (Alert alert : groupedAlerts) {
            Collections.sort(alert.groupedDescriptions);
            Collections.sort(alert.groupedLocalizedCities);
            alert.desc = TextUtils.join(", ", alert.groupedDescriptions);
            alert.localizedCity = TextUtils.join(", ", alert.groupedLocalizedCities);
        }
        return groupedAlerts;
    }

    void invalidateAlertList() {
        if (mIsReloading) {
            return;
        }

        int previousCount = mDisplayAlerts.size();
        mDisplayAlerts.clear();
        final long cutoffTimestamp = AppPreferences.getRecentAlertsCutoffTimestamp(getContext());

        if (cutoffTimestamp > 0) {
            for (Alert alert : mNewAlerts) {
                if (alert.date > cutoffTimestamp) {
                    mDisplayAlerts.add(alert);
                }
            }
        } else {
            mDisplayAlerts.addAll(mNewAlerts);
        }

        if (mAlertsAdapter != null) {
            // Check if new alerts were added at the top
            if (mHasLoaded && mDisplayAlerts.size() > previousCount && previousCount > 0) {
                // Animate new items sliding in from top
                mAlertsAdapter.notifyItemRangeInserted(0, mDisplayAlerts.size() - previousCount);

                // Scroll to top to show new alerts
                if (mAlertsList != null) {
                    mAlertsList.smoothScrollToPosition(0);
                }
            } else {
                mAlertsAdapter.notifyDataSetChanged();
                
                // Trigger restore animation if pending
                if (mPendingRestoreAnimation && mAlertsList != null) {
                    mPendingRestoreAnimation = false;
                    
                    // Hide list initially to prevent flash (items appear at alpha=1 before animation sets alpha=0)
                    mAlertsList.setAlpha(0f);
                    
                    // Wait for layout to complete before animating
                    mAlertsList.post(() -> {
                        // Show list now that we're about to set items invisible
                        mAlertsList.setAlpha(1f);
                        animateRestoreAlerts();
                    });
                }
            }
        }

        // Show/hide no alerts
        if (mNoAlerts == null) {
            return;
        }

        if (mDisplayAlerts.size() == 0) {
            // Fade in the no alerts view
            mNoAlerts.setAlpha(0f);
            mNoAlerts.setVisibility(View.VISIBLE);
            mNoAlerts.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        } else {
            mNoAlerts.setVisibility(View.GONE);
        }

        // Notify Activity to update clear button visibility
        if (getActivity() instanceof Main) {
            ((Main) getActivity()).updateClearRestoreButton();
        }
    }

    void startSkeletonAnimation() {
        if (mSkeletonLoading == null)
            return;

        // Start animation on all skeleton cards
        ViewGroup container = mSkeletonLoading.findViewById(R.id.skeleton_container);
        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View skeletonCard = container.getChildAt(i);
                if (skeletonCard != null) {
                    // Find all skeleton views and start animation
                    startViewAnimation(skeletonCard.findViewById(R.id.skeleton_image));
                    startViewAnimation(skeletonCard.findViewById(R.id.skeleton_title));
                    startViewAnimation(skeletonCard.findViewById(R.id.skeleton_desc));
                    startViewAnimation(skeletonCard.findViewById(R.id.skeleton_time));
                }
            }
        }
    }

    void startViewAnimation(View view) {
        if (view != null) {
            Animation animation = android.view.animation.AnimationUtils.loadAnimation(getContext(),
                    R.anim.shimmer_animation);
            view.startAnimation(animation);
        }
    }

    void stopSkeletonAnimation() {
        if (mSkeletonLoading == null)
            return;

        // Stop animation on all skeleton cards
        ViewGroup container = mSkeletonLoading.findViewById(R.id.skeleton_container);
        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View skeletonCard = container.getChildAt(i);
                if (skeletonCard != null) {
                    // Find all skeleton views and clear animation
                    clearViewAnimation(skeletonCard.findViewById(R.id.skeleton_image));
                    clearViewAnimation(skeletonCard.findViewById(R.id.skeleton_title));
                    clearViewAnimation(skeletonCard.findViewById(R.id.skeleton_desc));
                    clearViewAnimation(skeletonCard.findViewById(R.id.skeleton_time));
                }
            }
        }
    }

    void clearViewAnimation(View view) {
        if (view != null) {
            view.clearAnimation();
        }
    }



    public class GetRecentAlertsAsync extends AsyncTaskAdapter<Integer, String, Integer> {
        boolean mShowLoading;
        android.content.Context mContext;

        public GetRecentAlertsAsync(boolean showLoading) {
            mIsReloading = true;
            mShowLoading = showLoading;

            // Capture application context to avoid leaks and null pointers
            if (getActivity() != null) {
                mContext = getActivity().getApplicationContext();
            }

            if (mShowLoading) {
                // Show skeleton instead of progress bar
                if (mSkeletonLoading != null) {
                    mSkeletonLoading.setVisibility(View.VISIBLE);
                    startSkeletonAnimation();
                }
                if (mAlertsList != null) {
                    mAlertsList.setVisibility(View.GONE);
                }
            }
        }

        @Override
        protected Integer doInBackground(Integer... Parameter) {
            // Ensure we have a context
            if (mContext == null) {
                return -1;
            }
            return getRecentAlerts(mContext);
        }

        @Override
        protected void onPostExecute(Integer errorStringResource) {
            mIsReloading = false;
            mHasLoaded = true;

            // Cache the alerts
            if (mNewAlerts != null) {
                sCachedAlerts = new ArrayList<>(mNewAlerts);
            }

            if (getActivity() == null || isDetached()) {
                return;
            }

            if (mShowLoading) {
                // Smooth fade transition from skeleton to RecyclerView
                if (mSkeletonLoading != null && mAlertsList != null) {
                    // Stop skeleton animation
                    stopSkeletonAnimation();

                    // Fade out skeleton
                    mSkeletonLoading.animate()
                            .alpha(0f)
                            .setDuration(150)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    mSkeletonLoading.setVisibility(View.GONE);
                                    mSkeletonLoading.setAlpha(1f); // Reset for next time
                                }
                            });

                    // Fade in RecyclerView
                    mAlertsList.setAlpha(0f);
                    mAlertsList.setVisibility(View.VISIBLE);
                    mAlertsList.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .setStartDelay(50); // Minimal overlap for smooth transition
                }
            }

            if (mSwipeRefresh != null && mSwipeRefresh.isRefreshing()) {
                mSwipeRefresh.setRefreshing(false);
            }

            // Always invalidate list to ensure empty state is shown if needed
            invalidateAlertList();

            // Notify Activity to update menu button state
            if (getActivity() instanceof Main) {
                ((Main) getActivity()).updateClearRestoreButton();
            }

            if (errorStringResource != 0) {
                Toast.makeText(getContext(), getString(errorStringResource), Toast.LENGTH_LONG).show();
            }
        }
    }

    // Helper method for Activity to check if there are alerts to clear
    public boolean hasDisplayedAlerts() {
        return mDisplayAlerts != null && mDisplayAlerts.size() > 0;
    }
    // Flag to trigger restore animation after reload
    private boolean mPendingRestoreAnimation = false;
    
    // Handle clear/restore button click (called from Activity)
    public void handleClearRestoreClick() {
        // Check current state - is it cleared?
        long currentCutoff = AppPreferences.getRecentAlertsCutoffTimestamp(getContext());
        
        if (currentCutoff > 0) {
            // Currently cleared - restore alerts
            AppPreferences.updateRecentAlertsCutoffTimestamp(0, getContext());
            
            // Set flag to animate items appearing
            mPendingRestoreAnimation = true;
            
            // Reload alerts (animation will be triggered after layout)
            reloadRecentAlerts();
        } else if (mDisplayAlerts.size() > 0) {
            // Clear currently displayed alerts - show confirmation dialog
            AlertDialogBuilder.showGenericDialog(getString(R.string.clearRecentAlerts),
                    getString(R.string.clearRecentAlertsDesc), getString(R.string.yes), getString(R.string.no),
                    true, getContext(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            // Clicked okay?
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                // Animate alerts clearing with cascade fade-out
                                animateClearAlerts(() -> {
                                    // Set recent alerts cutoff timestamp to now
                                    AppPreferences.updateRecentAlertsCutoffTimestamp(DateTime.getUnixTimestamp(),
                                            getContext());

                                    // Reload alerts
                                    reloadRecentAlerts();

                                    // Clear app notifications
                                    AppNotifications.clearAll(getContext());
                                });
                            }
                        }
                    });
        }
    }
    
    // Animate all alert cards fading out before clearing
    private void animateClearAlerts(Runnable onComplete) {
        if (mAlertsList == null || mAlertsList.getChildCount() == 0) {
            onComplete.run();
            return;
        }
        
        int childCount = mAlertsList.getChildCount();
        long staggerDelay = 30; // ms between each item's animation start
        long animDuration = 200; // duration of each item's animation
        
        for (int i = 0; i < childCount; i++) {
            View child = mAlertsList.getChildAt(i);
            if (child != null) {
                child.animate()
                    .alpha(0f)
                    .translationY(child.getHeight() * 0.3f) // Slight slide down
                    .setStartDelay(i * staggerDelay)
                    .setDuration(animDuration)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .start();
            }
        }
        
        // Run completion after all animations finish
        long totalAnimTime = (childCount - 1) * staggerDelay + animDuration;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Reset all child view properties before calling onComplete
            // This prevents RecyclerView from reusing views with alpha=0
            for (int i = 0; i < mAlertsList.getChildCount(); i++) {
                View child = mAlertsList.getChildAt(i);
                if (child != null) {
                    child.setAlpha(1f);
                    child.setTranslationY(0f);
                }
            }
            onComplete.run();
        }, totalAnimTime);
    }
    
    // Animate alerts appearing with cascade fade-in (reverse of clear)
    private void animateRestoreAlerts() {
        if (mAlertsList == null || mAlertsList.getChildCount() == 0) {
            return;
        }
        
        int childCount = mAlertsList.getChildCount();
        long staggerDelay = 40; // ms between each item's animation start
        long animDuration = 250; // duration of each item's animation
        
        for (int i = 0; i < childCount; i++) {
            View child = mAlertsList.getChildAt(i);
            if (child != null) {
                // Start invisible and offset upward
                child.setAlpha(0f);
                child.setTranslationY(-child.getHeight() * 0.2f);
                
                // Animate to visible and normal position
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * staggerDelay)
                    .setDuration(animDuration)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            }
        }
    }
}