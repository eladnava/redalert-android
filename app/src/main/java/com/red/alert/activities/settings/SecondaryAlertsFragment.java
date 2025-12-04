package com.red.alert.activities.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.TwoStatePreference;
import androidx.preference.Preference;

import com.red.alert.R;
import com.red.alert.ui.dialogs.AlertDialogBuilder;

public class SecondaryAlertsFragment extends BasePreferenceFragment {
    TwoStatePreference mSecondaryAlertPopup;
    
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register for activity result (overlay permission)
        overlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Check if we have permission now
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(getActivity())) {
                    if (mSecondaryAlertPopup != null) {
                        mSecondaryAlertPopup.setChecked(true);
                    }
                }
            }
        );
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_secondary_alerts, rootKey);
        
        // Cache preferences
        mSecondaryAlertPopup = findPreference(getString(R.string.secondaryAlertPopupPref));
        
        // Secondary alert popup checkbox listener
        if (mSecondaryAlertPopup != null) {
            mSecondaryAlertPopup.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // Android M+: Check if we have permission to draw over other apps
                    if ((boolean) newValue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
                            && !Settings.canDrawOverlays(getActivity())) {
                        // Show permission request dialog
                        AlertDialogBuilder.showGenericDialog(
                                getString(R.string.grantOverlayPermission),
                                getString(R.string.grantOverlayPermissionInstructions),
                                getString(R.string.okay),
                                getString(R.string.notNow),
                                true,
                                getActivity(),
                                (dialogInterface, which) -> {
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + getActivity().getPackageName()));
                                        overlayPermissionLauncher.launch(intent);
                                    }
                                });
                        // Don't enable yet
                        return false;
                    }
                    return true;
                }
            });
        }
    }
}