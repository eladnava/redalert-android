package com.red.alert.activities;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;


import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.installations.FirebaseInstallations;
import com.red.alert.R;
import com.red.alert.activities.settings.GeneralPreferenceFragment;
import com.red.alert.config.Integrations;
import com.red.alert.config.Logging;
import com.red.alert.config.Safety;
import com.red.alert.config.Sound;
import com.red.alert.config.ThreatTypes;
import com.red.alert.logic.alerts.AlertLogic;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.logic.communication.intents.AlertPopupParameters;
import com.red.alert.logic.communication.intents.AlertViewParameters;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.logic.location.LocationLogic;
import com.red.alert.logic.push.FCMRegistration;
import com.red.alert.logic.push.PushManager;
import com.red.alert.logic.push.PushyRegistration;
import com.red.alert.logic.services.ServiceManager;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.model.res.VersionInfo;

import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.dialogs.custom.UpdateDialogs;
import com.red.alert.ui.fragments.AlertsFragment;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.ui.notifications.AppNotifications;
import com.red.alert.utils.backend.RedAlertAPI;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.feedback.Volume;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.integration.GooglePlayServices;
import com.red.alert.utils.localization.DateTime;
import com.red.alert.utils.localization.Localization;
import com.red.alert.utils.metadata.AppVersion;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.networking.HTTP;
import com.red.alert.utils.os.AndroidSettings;
import com.red.alert.utils.threading.AsyncTaskAdapter;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import me.pushy.sdk.Pushy;
import me.pushy.sdk.config.PushyForegroundService;
import me.pushy.sdk.util.PushyAuthentication;

public class Main extends AppCompatActivity {
    boolean mIsResumed;
    boolean mIsRegistering;
    boolean mCheckedForUpdates;
    boolean mPushTokensRefreshed;
    boolean mPermissionDialogDisplayed;
    boolean mIsSwitchingTabs;
    public boolean mShouldOpenCitiesDialog;
    int mCurrentTabId = R.id.nav_alerts; // Track current tab to prevent reloading

    ExtendedFloatingActionButton mImSafe;
    MaterialToolbar mToolbar;
    BottomNavigationView mBottomNav;

    OnBackPressedCallback mTabBackCallback;
    OnBackPressedCallback mInnerNavCallback;

    // Manual navigation stack - avoids fragment back stack which causes flash
    // issues
    java.util.Stack<NavigationEntry> mNavigationStack = new java.util.Stack<>();

    static class NavigationEntry {
        Class<? extends Fragment> fragmentClass;
        String title;

        NavigationEntry(Class<? extends Fragment> clazz, String title) {
            this.fragmentClass = clazz;
            this.title = title;
        }
    }

    SharedPreferences.OnSharedPreferenceChangeListener mBroadcastListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences Preferences, String Key) {
            // Language changed?
            if (Key.equalsIgnoreCase(SettingsEvents.THEME_OR_LANGUAGE_CHANGED)) {
                // Reload activity
                finish();
                startActivity(new Intent(Main.this, Main.class));
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        // Reapply locale
        Localization.overridePhoneLocale(base);
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Apply custom theme selection
        Localization.applyThemeSelection(this);

        super.onCreate(savedInstanceState);

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // RTL
        Localization.overridePhoneLocale(this);

        // Initialize UI
        initializeUI();

        // Handle intent extras (notification click)
        handleNotificationClick(getIntent());

        // Pixel compatibility
        forceForegroundServiceOnPixelDevices();

        // Load initial fragment
        if (savedInstanceState == null) {
            loadFragment(new AlertsFragment());
        }

        // NOTE: We no longer use the fragment back stack for inner navigation.
        // All navigation is handled manually via mNavigationStack and
        // mInnerNavCallback.

        // Handle back press to navigate to Alerts tab when on Settings or Map tabs at
        // root level
        // This callback is only enabled when at root level on non-alerts tabs
        mTabBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Navigate to Alerts tab
                mBottomNav.setSelectedItemId(R.id.nav_alerts);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, mTabBackCallback);

        // Handle back press for inner navigation (e.g., Advanced Settings -> General
        // Settings)
        // This callback has higher priority than mTabBackCallback
        mInnerNavCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Use popBackStack to trigger the proper pop animations
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    // Get the title we're navigating back to
                    String newTitle = null;
                    boolean willBeAtRoot = mNavigationStack.size() <= 1;
                    
                    if (!mNavigationStack.isEmpty()) {
                        NavigationEntry entry = mNavigationStack.pop();
                        newTitle = entry.title;
                    }
                    
                    // Invalidate menu immediately to hide alert-specific menu items
                    invalidateOptionsMenu();
                    
                    // Now pop the fragment (this triggers the exit animation)
                    getSupportFragmentManager().popBackStack();
                    
                    // Update toolbar immediately (no animation needed)
                    if (mToolbar != null && newTitle != null) {
                        mToolbar.setTitle(newTitle);
                        mToolbar.setSubtitle(null);
                        
                        if (willBeAtRoot) {
                            mToolbar.setNavigationIcon(null);
                            mToolbar.setNavigationOnClickListener(null);
                        }
                    }
                    
                    // Update callbacks if at root
                    if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                        setEnabled(false);
                        if (mBottomNav != null) {
                            updateTabBackCallbackState(mBottomNav.getSelectedItemId());
                        }
                    }
                } else {
                    // Stack is empty, disable and let other callbacks handle it
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, mInnerNavCallback);

        // Set initial state
        if (mBottomNav != null) {
            updateTabBackCallbackState(mBottomNav.getSelectedItemId());
        }
    }

    void updateTabBackCallbackState(int selectedId) {
        if (mTabBackCallback == null)
            return;

        // Only enable when at root level (navigation stack empty) AND on Settings or
        // Map tab
        boolean atRoot = mNavigationStack.isEmpty();
        boolean onNonAlertsTab = (selectedId == R.id.nav_settings || selectedId == R.id.nav_map);

        mTabBackCallback.setEnabled(atRoot && onNonAlertsTab);
    }

    public void updateTitle(CharSequence title) {
        if (mToolbar != null && title != null) {
            // Capitalize the title properly (first letter of each word)
            String titleStr = title.toString();
            if (titleStr.length() > 0) {
                // Simple capitalization - uppercase first letter
                titleStr = titleStr.substring(0, 1).toUpperCase() + titleStr.substring(1);
            }
            mToolbar.setTitle(titleStr);
        }
    }

    public void navigateToFragment(Fragment fragment, String title) {
        // Save current fragment info to navigation stack for back navigation
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            // Get current title from toolbar
            String currentTitle = mToolbar != null ? mToolbar.getTitle().toString() : "";
            mNavigationStack.push(new NavigationEntry(currentFragment.getClass(), currentTitle));
        }

        // Update title
        updateTitle(title);

        // Show back button
        if (mToolbar != null) {
            mToolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            mToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        // Enable inner navigation callback
        if (mInnerNavCallback != null) {
            mInnerNavCallback.setEnabled(true);
        }

        // Replace fragment with slide animations
        // For RTL languages, mirror the slide directions
        boolean isRTL = getResources().getConfiguration().getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL;
        
        int enterAnim, exitAnim, popEnterAnim, popExitAnim;
        if (isRTL) {
            // RTL: forward slides from left, back slides from right
            enterAnim = R.anim.slide_in_left;
            exitAnim = R.anim.slide_out_right;
            popEnterAnim = R.anim.slide_in_right;
            popExitAnim = R.anim.slide_out_left;
        } else {
            // LTR: forward slides from right, back slides from left
            enterAnim = R.anim.slide_in_right;
            exitAnim = R.anim.slide_out_left;
            popEnterAnim = R.anim.slide_in_left;
            popExitAnim = R.anim.slide_out_right;
        }
        
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    public void resetToRoot(String title, boolean showImSafe) {
        // Prevent title updates if we are switching tabs (clearing back stack)
        if (mIsSwitchingTabs) {
            return;
        }

        // Show/Hide ImSafe button
        if (mImSafe != null) {
            if (showImSafe)
                mImSafe.show();
            else
                mImSafe.hide();
        }

        // Update toolbar - immediate changes (animation was too visible)
        if (mToolbar != null) {
            mToolbar.setTitle(title);
            mToolbar.setSubtitle(null);
            mToolbar.setNavigationIcon(null);
            mToolbar.setNavigationOnClickListener(null);
        }
    }

    void initializeUI() {
        setContentView(R.layout.main);

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        RTLSupport.mirrorActionBar(this);

        mToolbar.setTitle(getString(R.string.recentAlerts));

        mImSafe = findViewById(R.id.safe);
        mBottomNav = findViewById(R.id.bottom_navigation);

        // Edge-to-edge: Apply window insets to AppBarLayout and BottomNavigationView
        View appBar = findViewById(R.id.app_bar);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mBottomNav, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        mBottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                
                // Don't reload if already on this tab
                if (itemId == mCurrentTabId) {
                    return true;
                }
                
                // Set flag BEFORE changing tab to hide all menu items during transition
                mIsSwitchingTabs = true;
                mCurrentTabId = itemId;

                // IMMEDIATELY update menu items visibility - synchronous, no delay
                updateMenuItemsVisibility();

                // Check if we have a back stack to clear (use manual stack)
                final boolean hasBackStack = !mNavigationStack.isEmpty();

                // Clear toolbar subtitle when switching tabs
                if (mToolbar != null) {
                    mToolbar.setSubtitle(null);
                }

                if (itemId == R.id.nav_alerts) {
                    loadFragmentForTab(new AlertsFragment(), hasBackStack);
                    mImSafe.show();
                    mToolbar.setTitle(getString(R.string.recentAlerts));
                    updateTabBackCallbackState(itemId);
                    return true;
                } else if (itemId == R.id.nav_map) {
                    loadFragmentForTab(new com.red.alert.ui.fragments.AlertsMapFragment(), hasBackStack);
                    mImSafe.hide();
                    mToolbar.setTitle(getString(R.string.alerts));
                    updateTabBackCallbackState(itemId);
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    loadFragmentForTab(new GeneralPreferenceFragment(), hasBackStack);
                    mImSafe.hide();
                    mToolbar.setTitle(getString(R.string.settings));
                    updateTabBackCallbackState(itemId);
                    return true;
                }
                return false;
            }
        });

        mImSafe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.imSafeMessage));
                if (Singleton.getSharedPreferences(Main.this).getBoolean(getString(R.string.imSafeWhatsAppPref),
                        false)) {
                    shareIntent.setPackage(Integrations.WHATSAPP_PACKAGE);
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.imSafeDesc)));
            }
        });
    }

    void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Standard fade animations
        transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);

        transaction.replace(R.id.fragment_container, fragment);
        // Use commitNow() for synchronous fragment lifecycle - ensures old fragment's
        // MenuProvider is removed before new one is added, preventing menu jitter
        transaction.commitNow();
    }

    void loadFragmentForTab(Fragment fragment, boolean clearBackStack) {
        if (clearBackStack && !mNavigationStack.isEmpty()) {
            mIsSwitchingTabs = true;

            // Clear our manual navigation stack
            mNavigationStack.clear();

            // Disable inner nav callback since we're at root
            if (mInnerNavCallback != null) {
                mInnerNavCallback.setEnabled(false);
            }

            // Hide back button IMMEDIATELY to prevent title jump
            if (mToolbar != null) {
                mToolbar.setNavigationIcon(null);
                mToolbar.setNavigationOnClickListener(null);
            }

            // Simply replace with the new fragment using a fade animation
            // Since we are not popping the back stack, we don't need to hide the container
            // manually
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
            transaction.replace(R.id.fragment_container, fragment);
            transaction.commitNow();

            mIsSwitchingTabs = false;
        } else {
            // Clear stack if switching tabs even without visual animation
            mNavigationStack.clear();
            if (mInnerNavCallback != null) {
                mInnerNavCallback.setEnabled(false);
            }
            // Normal fragment load with animation
            loadFragment(fragment);
            mIsSwitchingTabs = false;
        }
    }

    void openLiveMap() {
        com.red.alert.ui.fragments.MapFragment mapFragment = new com.red.alert.ui.fragments.MapFragment();
        Bundle args = new Bundle();
        args.putBoolean(AlertViewParameters.LIVE, true);
        mapFragment.setArguments(args);
        loadFragment(mapFragment);

        // Update UI state
        mBottomNav.setSelectedItemId(R.id.nav_map);
        mImSafe.hide();
        mToolbar.setTitle(getString(R.string.alerts));
    }

    public void onMapFragmentInteraction(String title, String subtitle) {
        // Save current fragment info to navigation stack for back navigation
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null && mToolbar != null) {
            String currentTitle = mToolbar.getTitle() != null ? mToolbar.getTitle().toString() : "";
            mNavigationStack.push(new NavigationEntry(currentFragment.getClass(), currentTitle));
        }
        
        // Hide "I'm safe" button
        if (mImSafe != null) {
            mImSafe.hide();
        }

        // Invalidate menu immediately to hide clear button when navigating to detail
        invalidateOptionsMenu();

        // Update toolbar immediately (no animation needed)
        if (mToolbar != null) {
            mToolbar.setTitle(title);
            mToolbar.setSubtitle(subtitle);
            mToolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            mToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        // Enable inner navigation callback for back handling
        if (mInnerNavCallback != null) {
            mInnerNavCallback.setEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RTLSupport.mirrorActionBar(this);
        mIsResumed = true;
        mPermissionDialogDisplayed = false;

        AppNotifications.clearAll(this);
        Broadcasts.subscribe(this, mBroadcastListener);

        requestNotificationPermission();
        showBatteryExemptionDialog();
        showLocationPermissionDialog();
        showScheduleExactAlarmsPermissionDialog();
        showAlertPopupPermissionDialog();
        showNotificationPolicyAccessPermissionDialog();
        showForcedForegroundServiceDialog();
        showAppUpdateAvailableDialog();

        ServiceManager.startAppServices(this);

        if (!mIsRegistering) {
            new RegisterPushAsync().execute();
        }

        // Set up UI
        // initializeUI(); // REMOVED: Do not recreate UI in onResume, it detaches the
        // fragment!

        // NOTE: Back stack listener is registered once in onCreate(), not here
        // to avoid duplicate registrations on each resume

        // Check for updates
        new CheckForUpdatesAsync().execute();
    }

    void forceForegroundServiceOnPixelDevices() {
        if (!Build.MANUFACTURER.toLowerCase().contains("google") || !Build.MODEL.toLowerCase().contains("pixel")
                || Build.VERSION.SDK_INT < 35) {
            return;
        }
        if (!AppPreferences.getForegroundServiceEnabled(this)) {
            Singleton.getSharedPreferences(this).edit().putBoolean(getString(R.string.foregroundServicePref), true)
                    .commit();
        }
    }

    void showAppUpdateAvailableDialog() {
        if (!AppPreferences.getTutorialDisplayed(this))
            return;
        if (!RedAlertAPI.isRegistered(Main.this))
            return;
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
            return;
        if (mPermissionDialogDisplayed)
            return;
        if (mCheckedForUpdates)
            return;
        mCheckedForUpdates = true;
        new CheckForUpdatesAsync().execute();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        AppNotifications.clearAll(this);
        RTLSupport.mirrorActionBar(this);
        handleNotificationClick(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsResumed = false;
    }

    void requestNotificationPermission() {
        // Check if device is already registered, but permission not granted yet
        if (PushyAuthentication.getDeviceCredentials(this) != null && (!Pushy.isPermissionGranted(this) || !NotificationManagerCompat.from(Main.this).areNotificationsEnabled())) {
            mPermissionDialogDisplayed = true;

            // Show error with an on-click listener that opens the App Info page
            AlertDialogBuilder.showGenericDialog(getString(R.string.error), getString(R.string.grantNotificationPermission), getString(R.string.okay), null, false, Main.this, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Open App Info settings page
                    AndroidSettings.openAppInfoPage(Main.this);
                }
            });
        }
    }

    void showLocationPermissionDialog() {
        if (!AppPreferences.getLocationAlertsEnabled(this))
            return;
        if (mPermissionDialogDisplayed)
            return;
        if (!LocationLogic.isLocationAccessGranted(this)) {
            LocationLogic.showLocationAccessRequestDialog(this);
            mPermissionDialogDisplayed = true;
        }
    }

    void showScheduleExactAlarmsPermissionDialog() {
        if (mPermissionDialogDisplayed)
            return;
        if (!AppPreferences.getTutorialDisplayed(this))
            return;
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
            return;
        if (Build.VERSION.SDK_INT < 31)
            return;
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName()))
            return;
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager.canScheduleExactAlarms())
            return;
        AlertDialogBuilder.showGenericDialog(getString(R.string.allowSettingExactAlarms),
                getString(R.string.allowSettingExactAlarmsInstructions), getString(R.string.okay),
                getString(R.string.notNow), true, this, (dialogInterface, which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    }
                });
        mPermissionDialogDisplayed = true;
    }

    void showBatteryExemptionDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        if (!AppPreferences.getTutorialDisplayed(this))
            return;
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
            return;
        if (mPermissionDialogDisplayed)
            return;
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName()))
            return;
        int count = Singleton.getSharedPreferences(this)
                .getInt(getString(R.string.batteryOptimizationWarningDisplayedCountPref), 0);
        if (count >= 3)
            return;
        AlertDialogBuilder.showGenericDialog(getString(R.string.disableBatteryOptimizations),
                AndroidSettings.getBatteryOptimizationWhitelistInstructions(this), getString(R.string.okay),
                getString(R.string.notNow), true, this, (dialogInterface, which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                });
        mPermissionDialogDisplayed = true;
        Singleton.getSharedPreferences(this).edit()
                .putInt(getString(R.string.batteryOptimizationWarningDisplayedCountPref), count + 1).commit();
    }

    void showAlertPopupPermissionDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        if (!AppPreferences.getPopupEnabled(this) && !AppPreferences.getSecondaryPopupEnabled(this))
            return;
        if (Settings.canDrawOverlays(this))
            return;
        if (!AppPreferences.getTutorialDisplayed(this))
            return;
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
            return;
        if (mPermissionDialogDisplayed)
            return;
        mPermissionDialogDisplayed = true;
        AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission),
                getString(R.string.grantOverlayPermissionInstructions), getString(R.string.okay),
                getString(R.string.notNow), true, this, (dialogInterface, which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE)
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                });
    }

    void showNotificationPolicyAccessPermissionDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return;
        if (!SoundLogic.isSamsungDeviceRequiringSilentModeOverride())
            return;
        if (!SoundLogic.shouldOverrideSilentMode(AlertTypes.PRIMARY, this)
                && !SoundLogic.shouldOverrideSilentMode(AlertTypes.SECONDARY, this))
            return;
        String alertSoundName = SoundLogic.getAlertSoundName(AlertTypes.PRIMARY, ThreatTypes.MISSILES, null, this);
        if (!alertSoundName.equals(Sound.CUSTOM_SOUND_NAME))
            return; // simplification
        if (!AppPreferences.getTutorialDisplayed(this))
            return;
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
            return;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager.isNotificationPolicyAccessGranted())
            return;
        if (mPermissionDialogDisplayed)
            return;
        mPermissionDialogDisplayed = true;
        AlertDialogBuilder.showGenericDialog(getString(R.string.notificationPolicyAccessPermission),
                getString(R.string.notificationPolicyAccessPermissionInstructions), getString(R.string.okay),
                getString(R.string.notNow), true, this, (dialogInterface, which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE)
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                });
    }

    void showForcedForegroundServiceDialog() {
        if (!Build.MANUFACTURER.toLowerCase().contains("google") || !Build.MODEL.toLowerCase().contains("pixel")
                || Build.VERSION.SDK_INT < 35)
            return;
        if (!AppPreferences.getTutorialDisplayed(this))
            return;
        if (!FCMRegistration.isRegistered(this) || !PushyRegistration.isRegistered(this))
            return;
        if (Singleton.getSharedPreferences(this).getBoolean(getString(R.string.forcedForegroundServiceDialogShown),
                false))
            return;
        if (mPermissionDialogDisplayed)
            return;
        mPermissionDialogDisplayed = true;
        AlertDialogBuilder.showGenericDialog(getString(R.string.hidePushyForegroundNotification),
                getString(R.string.hidePushyForegroundNotificationInstructions), getString(R.string.okay),
                getString(R.string.notNow), true, this, (dialogInterface, which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        new Thread(() -> {
                            NotificationManager notificationManager = getSystemService(NotificationManager.class);
                            while (notificationManager.getNotificationChannel(
                                    PushyForegroundService.FOREGROUND_NOTIFICATION_CHANNEL) == null) {
                                try {
                                    Thread.sleep(200);
                                } catch (Exception exc) {
                                }
                            }
                            if (isDestroyed() || isFinishing())
                                return;
                            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                            intent.putExtra(Settings.EXTRA_CHANNEL_ID,
                                    PushyForegroundService.FOREGROUND_NOTIFICATION_CHANNEL);
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(intent);
                        }).start();
                    }
                });
        Singleton.getSharedPreferences(this).edit()
                .putBoolean(getString(R.string.forcedForegroundServiceDialogShown), true).commit();
    }

    private String checkForUpdates() {
        String updateJson;
        try {
            updateJson = HTTP.get("/update/android");
        } catch (Exception exc) {
            return null;
        }
        VersionInfo updateInfo;
        try {
            updateInfo = Singleton.getJackson().readValue(updateJson, VersionInfo.class);
        } catch (Exception exc) {
            return null;
        }
        if (!updateInfo.showDialog)
            return null;
        if (updateInfo.versionCode > AppVersion.getVersionCode(this))
            return updateInfo.version;
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Broadcasts.unsubscribe(this, mBroadcastListener);
    }

    void showRegistrationSuccessDialog() {
        if (AppPreferences.getTutorialDisplayed(this))
            return;
        String desc = getString(R.string.pushRegistrationSuccessDesc);
        if (Singleton.getSharedPreferences(this).getBoolean("tutorial_1_0_22", false)) {
            desc = getString(R.string.pushRegistrationReselectDesc);
        }
        AlertDialogBuilder.showGenericDialog(getString(R.string.pushRegistrationSuccess), desc,
                getString(R.string.okay), null, false, this, (dialogInterface, i) -> {
                    // Set flag to auto-open cities dialog
                    mShouldOpenCitiesDialog = true;
                    // Switch to settings tab
                    mBottomNav.setSelectedItemId(R.id.nav_settings);
                });
        AppPreferences.setTutorialDisplayed(this);
    }

    public class RegisterPushAsync extends AsyncTaskAdapter<Integer, String, Exception> {
        androidx.appcompat.app.AlertDialog mLoading;

        public RegisterPushAsync() {
            mIsRegistering = true;
            mPushTokensRefreshed = true;

            // Use Material 3 styled dialog
            com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                    Main.this);
            View view = getLayoutInflater().inflate(R.layout.dialog_progress, null);
            TextView message = view.findViewById(R.id.message);
            message.setText(getString(R.string.signing_up));
            builder.setView(view);
            builder.setCancelable(false);
            mLoading = builder.create();

            if (!FCMRegistration.isRegistered(Main.this) || !PushyRegistration.isRegistered(Main.this)) {
                mLoading.show();
            }
        }

        @Override
        protected Exception doInBackground(Integer... Parameter) {
             if (!GooglePlayServices.isAvailable(Main.this))
                 return new Exception("This app requires Google Play Services.");

             String previousPushyToken = PushyRegistration.getRegistrationToken(Main.this);
             String previousFirebaseToken = FCMRegistration.getRegistrationToken(Main.this);

             int tries = 0;

             while (tries <= 5) {
                 try {
                     tries++;

                     String pushyToken = PushyRegistration.registerForPushNotifications(Main.this);
                     String fcmToken = FCMRegistration.registerForPushNotifications(Main.this);

                     if (!RedAlertAPI.isRegistered(Main.this)) {
                         RedAlertAPI.register(fcmToken, pushyToken, Main.this);
                     } else {
                         if ((previousFirebaseToken != null && !previousFirebaseToken.equals(fcmToken))
                                 || (previousPushyToken != null && !previousPushyToken.equals(pushyToken))) {
                             RedAlertAPI.updatePushTokens(fcmToken, pushyToken, Main.this);
                         }
                     }

                     if (!RedAlertAPI.isSubscribed(Main.this)) {
                         PushManager.updateSubscriptions(Main.this);
                         RedAlertAPI.updateNotificationPreferences(Main.this);
                         RedAlertAPI.subscribe(Main.this);
                     }

                     break;
                 } catch (Exception exc) {
                     if (exc.getMessage() != null && exc.getMessage().contains("Invalid argument for the given fid")) {
                         try {
                             Tasks.await(FirebaseInstallations.getInstance().delete());
                         } catch (Exception e) {
                         }
                     }

                     if (tries > 5)
                         return exc;

                     try {
                         Thread.sleep(1000);
                     } catch (InterruptedException e) {
                     }
                 }
             }

             return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            mIsRegistering = false;
            if (isFinishing() || isDestroyed())
                return;
            if (mLoading.isShowing())
                mLoading.dismiss();
            if (exc != null) {
                String errorMessage = getString(R.string.pushRegistrationFailed) + "\n\n" + exc.getMessage()
                        + (exc.getCause() != null ? "\n\n" + exc.getCause() : "");
                AlertDialogBuilder.showGenericDialog(getString(R.string.error), errorMessage, getString(R.string.okay),
                        null, false, Main.this, null);
            } else {
                showRegistrationSuccessDialog();
            }
        }
    }

    public class CheckForUpdatesAsync extends AsyncTaskAdapter<Integer, String, String> {
        @Override
        protected String doInBackground(Integer... Parameter) {
            return checkForUpdates();
        }

        @Override
        protected void onPostExecute(String newVersion) {
            if (isFinishing() || isDestroyed())
                return;
            if (!StringUtils.stringIsNullOrEmpty(newVersion))
                UpdateDialogs.showUpdateDialog(Main.this, newVersion);
        }
    }

    void handleNotificationClick(Intent intent) {
        String[] cities = intent.getStringArrayExtra(AlertPopupParameters.CITIES);
        String threatType = intent.getStringExtra(AlertPopupParameters.THREAT_TYPE);
        String instructions = intent.getStringExtra(AlertPopupParameters.INSTRUCTIONS);
        long timestamp = intent.getLongExtra(AlertPopupParameters.TIMESTAMP, 0);
        if (cities != null && cities.length > 0 && threatType != null && timestamp > 0) {
            int countdown = LocationData.getPrioritizedCountdownForCities(cities, this);
            if (!threatType.equals(ThreatTypes.EARLY_WARNING) && !threatType.equals(ThreatTypes.LEAVE_SHELTER)
                    && timestamp < (DateTime.getUnixTimestamp() - countdown - (Safety.POST_IMPACT_WAIT_MINUTES * 60)))
                return;
            final Intent popupIntent = new Intent();
            popupIntent.setClass(this, Popup.class);
            popupIntent.putExtra(AlertPopupParameters.CITIES, cities);
            popupIntent.putExtra(AlertPopupParameters.TIMESTAMP, timestamp);
            popupIntent.putExtra(AlertPopupParameters.THREAT_TYPE, threatType);
            popupIntent.putExtra(AlertPopupParameters.INSTRUCTIONS, instructions);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            new Handler().postDelayed(() -> startActivity(popupIntent), 300);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LocationLogic.LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 1
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            if (AppPreferences.getLocationAlertsEnabled(this))
                ServiceManager.startLocationService(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Always inflate menu - contains all action bar items for all tabs
        // Visibility is managed in onPrepareOptionsMenu based on current tab
        getMenuInflater().inflate(R.menu.menu_main, menu);
        
        // Store references to menu items for direct manipulation when switching tabs
        mShareMenuItem = menu.findItem(R.id.action_share);
        mLoadingMenuItem = menu.findItem(R.id.action_loading);
        mClearRestoreMenuItem = menu.findItem(R.id.action_clear_restore);
        
        // Set initial visibility based on current tab
        updateMenuItemsVisibility();
        
        return true;
    }
    
    // Menu item references for direct manipulation
    private MenuItem mShareMenuItem;
    private MenuItem mClearRestoreMenuItem;
    
    // Directly update menu items visibility - called synchronously when switching tabs
    private void updateMenuItemsVisibility() {
        boolean isMapTab = (mCurrentTabId == R.id.nav_map);
        boolean isAlertsTab = (mCurrentTabId == R.id.nav_alerts);
        
        // Share button - only on Map tab
        if (mShareMenuItem != null) {
            mShareMenuItem.setVisible(isMapTab);
        }
        
        // Loading indicator - hide on non-map tabs
        if (mLoadingMenuItem != null && !isMapTab) {
            mLoadingMenuItem.setVisible(false);
        }
        
        // Clear/Restore button - only on Alerts tab
        if (mClearRestoreMenuItem != null) {
            if (isAlertsTab) {
                // Get cutoff timestamp
                long cutoffTimestamp = AppPreferences.getRecentAlertsCutoffTimestamp(this);
                
                // If cutoff > 0, alerts are cleared - show restore icon
                if (cutoffTimestamp > 0) {
                    mClearRestoreMenuItem.setIcon(R.drawable.ic_restore_outline);
                    mClearRestoreMenuItem.setTitle(getString(R.string.restoreRecentAlerts));
                    mClearRestoreMenuItem.setVisible(true);
                } else {
                    // Check if fragment has alerts to clear
                    Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (currentFragment instanceof AlertsFragment) {
                        boolean hasAlerts = ((AlertsFragment) currentFragment).hasDisplayedAlerts();
                        mClearRestoreMenuItem.setIcon(R.drawable.ic_clear_all);
                        mClearRestoreMenuItem.setTitle(getString(R.string.clearRecentAlerts));
                        mClearRestoreMenuItem.setVisible(hasAlerts);
                    } else {
                        // Fragment not loaded yet, show button by default (will be updated later)
                        mClearRestoreMenuItem.setIcon(R.drawable.ic_clear_all);
                        mClearRestoreMenuItem.setVisible(true);
                    }
                }
            } else {
                mClearRestoreMenuItem.setVisible(false);
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Get menu items
        MenuItem shareItem = menu.findItem(R.id.action_share);
        MenuItem loadingItem = menu.findItem(R.id.action_loading);
        MenuItem clearRestoreItem = menu.findItem(R.id.action_clear_restore);
        
        boolean isMapTab = (mCurrentTabId == R.id.nav_map);
        boolean isAlertsTab = (mCurrentTabId == R.id.nav_alerts);
        
        // Share button - only on Map tab
        if (shareItem != null) {
            shareItem.setVisible(isMapTab);
        }
        
        // Loading indicator - visibility managed by Map fragment, but hide on other tabs
        if (loadingItem != null && !isMapTab) {
            loadingItem.setVisible(false);
        }
        
        // Clear/Restore button - only on Alerts tab
        if (clearRestoreItem != null) {
            if (isAlertsTab) {
                // Get cutoff timestamp
                long cutoffTimestamp = AppPreferences.getRecentAlertsCutoffTimestamp(this);
                
                // If cutoff > 0, alerts are cleared - show restore icon
                if (cutoffTimestamp > 0) {
                    clearRestoreItem.setIcon(R.drawable.ic_restore_outline);
                    clearRestoreItem.setTitle(getString(R.string.restoreRecentAlerts));
                    clearRestoreItem.setVisible(true);
                } else {
                    // Check if fragment has alerts to clear
                    Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (currentFragment instanceof AlertsFragment) {
                        boolean hasAlerts = ((AlertsFragment) currentFragment).hasDisplayedAlerts();
                        clearRestoreItem.setIcon(R.drawable.ic_clear_all);
                        clearRestoreItem.setTitle(getString(R.string.clearRecentAlerts));
                        clearRestoreItem.setVisible(hasAlerts);
                    } else {
                        clearRestoreItem.setVisible(false);
                    }
                }
            } else {
                clearRestoreItem.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            // Get current fragment and trigger share
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof com.red.alert.ui.fragments.AlertsMapFragment) {
                ((com.red.alert.ui.fragments.AlertsMapFragment) currentFragment).handleShareClick();
            }
            return true;
        }
        if (item.getItemId() == R.id.action_clear_restore) {
            // Get current fragment
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof AlertsFragment) {
                ((AlertsFragment) currentFragment).handleClearRestoreClick();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Called by AlertsFragment when alerts are loaded/changed to update menu
    public void updateClearRestoreButton() {
        updateMenuItemsVisibility();
    }
    
    // Loading indicator menu item reference
    private MenuItem mLoadingMenuItem;
    
    // Called by Map fragment to show/hide loading indicator
    public void setMapLoadingVisible(boolean show, boolean animate) {
        if (mLoadingMenuItem == null) return;
        
        View actionView = mLoadingMenuItem.getActionView();
        if (actionView == null) {
            mLoadingMenuItem.setVisible(show);
            return;
        }
        
        if (show) {
            if (animate) {
                actionView.setAlpha(0f);
                mLoadingMenuItem.setVisible(true);
                actionView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            } else {
                mLoadingMenuItem.setVisible(true);
            }
        } else {
            if (animate && mLoadingMenuItem.isVisible()) {
                actionView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> mLoadingMenuItem.setVisible(false))
                    .start();
            } else {
                mLoadingMenuItem.setVisible(false);
            }
        }
    }
    
    // Get reference to loading menu item after menu creation
    public void setLoadingMenuItem(MenuItem item) {
        mLoadingMenuItem = item;
    }
}
