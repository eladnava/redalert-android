package com.red.alert.activities.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.view.MenuItem;

import com.red.alert.R;
import com.red.alert.activities.settings.alerts.SecondaryAlerts;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.activities.AppCompatPreferenceActivity;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.feedback.Volume;

import me.pushy.sdk.Pushy;
import me.pushy.sdk.services.PushySocketService;
import me.pushy.sdk.util.PushyServiceManager;

public class Advanced extends AppCompatPreferenceActivity {
    Preference mSecondaryAlerts;
    SliderPreference mVolumeSelection;

    CheckBoxPreference mAlertPopup;
    CheckBoxPreference mForegroundService;

    static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load UI elements
        initializeUI();

        // Volume keys should control alert volume
        Volume.setVolumeKeysAction(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Support for RTL languages
        RTLSupport.mirrorActionBar(this);
    }

    void initializeUI() {
        // Allow click on home button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Load settings from XML There is no non-deprecated way to do it on API Level 7
        addPreferencesFromResource(R.xml.settings_advanced);

        // Cache resource IDs
        mSecondaryAlerts = findPreference(getString(R.string.secondaryPref));
        mVolumeSelection = (SliderPreference) findPreference(getString(R.string.volumePref));
        mAlertPopup = (CheckBoxPreference)findPreference(getString(R.string.alertPopupPref));
        mForegroundService = (CheckBoxPreference)findPreference(getString(R.string.foregroundServicePref));

        // Set up listeners
        initializeListeners();
    }

    void initializeListeners() {
        // Volume selection
        mVolumeSelection.setSeekBarChangedListener(new SliderPreference.onSeekBarChangedListener() {
            @Override
            public String getDialogMessage(float Value) {
                // Get slider percent
                int percent = (int) (AppPreferences.getPrimaryAlertVolume(Advanced.this, Value) * 100);

                // Generate summary
                return getString(R.string.volumeDesc) + "\r\n(" + percent + "%)";
            }
        });

        // Set up secondary alerts click listener
        mSecondaryAlerts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Prepare new intent
                Intent secondaryAlerts = new Intent();

                // Set class
                secondaryAlerts.setClass(Advanced.this, SecondaryAlerts.class);

                // Show settings
                startActivity(secondaryAlerts);

                // Consume event
                return true;
            }
        });

        // Alert popup checkbox listener
        mAlertPopup.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Android M+: Check if we have permission to draw over other apps
                if ((boolean)newValue == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(Advanced.this)) {
                    // Show permission request dialog
                    AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission), getString(R.string.grantOverlayPermissionInstructions), getString(R.string.okay), getString(R.string.notNow), true, Advanced.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            // Clicked okay?
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                // Bring user to relevant settings activity to grant the app overlay permission
                                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                            }
                        }
                    });

                    // Tell Android *not* to persist new checkbox value
                    return false;
                }

                // Tell Android to persist new checkbox value
                return true;
            }
        });

        // Foreground service checkbox listener
        mForegroundService.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                // Stop existing service
                PushyServiceManager.stop(Advanced.this);

                // Toggle foreground service
                Pushy.toggleForegroundService((boolean)value, Advanced.this);

                // Wait 2 seconds
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Start service again
                        PushyServiceManager.start(Advanced.this);
                    }
                }, 2000);

                // Tell Android to persist new checkbox value
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // User returned from manage overlay permission activity?
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            // Android M+: Check if we have permission to draw over other apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(Advanced.this)) {
                mAlertPopup.setChecked(true);
            }
        }
    }
}
