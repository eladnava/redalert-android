package com.red.alert.activities.settings.alerts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.red.alert.R;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.elements.dialogs.SliderPreferenceDialogFragmentCompat;

public class LocationAlertsPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private LocationAlerts mActivity;
    private Preference mNearbyCities;
    private SliderPreference mFrequency;
    private SliderPreference mMaxDistance;
    private SwitchPreferenceCompat mLocationAlerts;

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // Try casting the preference to the custom preference
        if (preference instanceof SliderPreference) {
            // Create a new instance of SliderPreferenceDialogFragment with a key
            DialogFragment dialogFragment = SliderPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), null);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof LocationAlerts) {
            mActivity = (LocationAlerts) context;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_location_alerts, rootKey);

        // Cache resource IDs
        mLocationAlerts = (SwitchPreferenceCompat) findPreference(getString(R.string.locationAlertsPref));
        mFrequency = (SliderPreference) findPreference(getString(R.string.gpsFrequencyPref));
        mMaxDistance = (SliderPreference) findPreference(getString(R.string.maxDistancePref));
        mNearbyCities = findPreference(getString(R.string.nearbyCitiesPref));

        // Notify activity that preferences are ready
        if (mActivity != null) {
            mActivity.onFragmentPreferencesReady();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mActivity != null) {
            mActivity.onPreferenceChanged(key);
        }
    }

    // Getters for activity to access preferences
    public Preference getNearbyCities() { return mNearbyCities; }
    public SliderPreference getFrequency() { return mFrequency; }
    public SliderPreference getMaxDistance() { return mMaxDistance; }
    public SwitchPreferenceCompat getLocationAlerts() { return mLocationAlerts; }
}
