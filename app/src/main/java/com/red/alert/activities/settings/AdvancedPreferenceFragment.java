package com.red.alert.activities.settings;

import android.os.Bundle;
import androidx.preference.Preference;
import com.red.alert.R;
import com.red.alert.ui.dialogs.AlertDialogBuilder;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class AdvancedPreferenceFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_advanced, rootKey);

        // Secondary Alerts
        findPreference(getString(R.string.secondaryPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity()).navigateToFragment(
                                    new SecondaryAlertsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Early Warnings
        findPreference(getString(R.string.earlyWarningsPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity())
                                    .navigateToFragment(new EarlyWarningsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Leave Shelter Alerts
        findPreference(getString(R.string.leaveShelterAlertsPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity()).navigateToFragment(
                                    new LeaveShelterAlertsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Location Alerts
        findPreference(getString(R.string.locationPref))
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() instanceof com.red.alert.activities.Main) {
                            ((com.red.alert.activities.Main) getActivity())
                                    .navigateToFragment(new LocationAlertsFragment(), preference.getTitle().toString());
                        }
                        return true;
                    }
                });

        // Alert Popup
        findPreference(getString(R.string.alertPopupPref))
                .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        // Enabled?
                        if ((boolean) newValue) {
                            // Check for overlay permission
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                    && !Settings.canDrawOverlays(getActivity())) {
                                // Show permission dialog
                                AlertDialogBuilder.showGenericDialog(getString(R.string.grantOverlayPermission),
                                        getString(R.string.grantOverlayPermissionInstructions),
                                        getString(R.string.okay),
                                        getString(R.string.notNow), true, getActivity(), (dialogInterface, which) -> {
                                            if (which == DialogInterface.BUTTON_POSITIVE)
                                                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:" + getActivity().getPackageName())));
                                        });

                                // Don't enable yet
                                return false;
                            }
                        }
                        return true;
                    }
                });
    }
}