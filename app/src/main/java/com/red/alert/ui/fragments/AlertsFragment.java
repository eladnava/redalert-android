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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    int mPreviousAlertCount = 0;
    Timer mPollingTimer;

    List<Alert> mNewAlerts;
    List<Alert> mDisplayAlerts;

    MenuItem mClearRecentAlertsItem;

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
    // Static flag to track if we've loaded at least once (prevents skeleton on tab switch)
    private static boolean sHasLoadedOnce;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
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
                returnTransition
                        .setFadeMode(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_CROSS);
                returnTransition.setScrimColor(android.graphics.Color.TRANSPARENT);
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
                    // Format time
                    java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm",
                            java.util.Locale.getDefault());
                    String time = format.format(new java.util.Date(alert.date * 1000));
                    String title = getString(R.string.alertAt) + " " + time;
                    ((com.red.alert.activities.Main) getActivity()).onMapFragmentInteraction(title);
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
            // Cache exists, so we've loaded before
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;

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
            // Only show skeleton on first ever load (not on tab switch or refresh)
            boolean showLoading = !sHasLoadedOnce && (sCachedAlerts == null || sCachedAlerts.isEmpty());

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
            if (sHasLoadedOnce && mDisplayAlerts.size() > previousCount && previousCount > 0) {
                // Animate new items sliding in from top
                mAlertsAdapter.notifyItemRangeInserted(0, mDisplayAlerts.size() - previousCount);

                // Scroll to top to show new alerts
                if (mAlertsList != null) {
                    mAlertsList.smoothScrollToPosition(0);
                }
            } else {
                mAlertsAdapter.notifyDataSetChanged();
            }
        }

        // Show/hide no alerts
        if (mNoAlerts == null) {
            return;
        }

        if (mDisplayAlerts.size() == 0) {
            // Fade in the no alerts message smoothly
            if (mNoAlerts.getVisibility() != View.VISIBLE) {
                mNoAlerts.setAlpha(0f);
                mNoAlerts.setVisibility(View.VISIBLE);
                mNoAlerts.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start();
            }
        } else {
            mNoAlerts.setVisibility(View.GONE);
        }

        // Update clear button visibility
        if (mClearRecentAlertsItem != null) {
            mClearRecentAlertsItem.setVisible(mDisplayAlerts.size() > 0);
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

    @Override
    public void onCreateOptionsMenu(@NonNull Menu OptionsMenu, @NonNull MenuInflater inflater) {
        // Add clear recent alerts button
        initializeClearRecentAlertsButton(OptionsMenu);

        super.onCreateOptionsMenu(OptionsMenu, inflater);
    }

    void initializeClearRecentAlertsButton(Menu OptionsMenu) {
        // Add clear item
        mClearRecentAlertsItem = OptionsMenu.add(Menu.NONE, Menu.NONE, Menu.NONE,
                getString(R.string.clearRecentAlerts));

        // Set default icon
        mClearRecentAlertsItem.setIcon(R.drawable.ic_clear_all);

        // Specify the show flags
        MenuItemCompat.setShowAsAction(mClearRecentAlertsItem, MenuItem.SHOW_AS_ACTION_ALWAYS);

        // On click, toggle clearing recent alerts
        mClearRecentAlertsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Clear currently displayed alerts
                if (mDisplayAlerts.size() > 0) {
                    // Show a dialog to the user
                    AlertDialogBuilder.showGenericDialog(getString(R.string.clearRecentAlerts),
                            getString(R.string.clearRecentAlertsDesc), getString(R.string.yes), getString(R.string.no),
                            true, getContext(), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int which) {
                                    // Clicked okay?
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        // Set recent alerts cutoff timestamp to now
                                        AppPreferences.updateRecentAlertsCutoffTimestamp(DateTime.getUnixTimestamp(),
                                                getContext());

                                        // Reload alerts
                                        reloadRecentAlerts();

                                        // Clear app notifications
                                        AppNotifications.clearAll(getContext());
                                    }
                                }
                            });
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
            sHasLoadedOnce = true;

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

            if (errorStringResource != 0) {
                Toast.makeText(getContext(), getString(errorStringResource), Toast.LENGTH_LONG).show();
            }
        }
    }
}