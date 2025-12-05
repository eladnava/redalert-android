package com.red.alert.activities.settings.alerts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.red.alert.R;
import com.red.alert.ui.elements.SearchableMultiSelectPreference;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.elements.dialogs.SliderPreferenceDialogFragmentCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;

public class SecondaryAlertsPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SecondaryAlerts mActivity;
    private SliderPreference mSecondaryVolume;
    private SwitchPreferenceCompat mSecondaryAlertPopup;
    private SwitchPreferenceCompat mSecondaryNotificationsEnabled;
    private SearchableMultiSelectPreference mSecondaryCitySelection;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof SecondaryAlerts) {
            mActivity = (SecondaryAlerts) context;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_secondary_alerts, rootKey);

        // Cache resource IDs
        mSecondaryVolume = (SliderPreference) findPreference(getString(R.string.secondaryVolumePref));
        mSecondaryAlertPopup = (SwitchPreferenceCompat) findPreference(getString(R.string.secondaryAlertPopupPref));
        mSecondaryCitySelection = (SearchableMultiSelectPreference) findPreference(getString(R.string.selectedSecondaryCitiesPref));
        mSecondaryNotificationsEnabled = (SwitchPreferenceCompat) findPreference(getString(R.string.secondaryEnabledPref));

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
    public SliderPreference getSecondaryVolume() { return mSecondaryVolume; }
    public SwitchPreferenceCompat getSecondaryAlertPopup() { return mSecondaryAlertPopup; }
    public SwitchPreferenceCompat getSecondaryNotificationsEnabled() { return mSecondaryNotificationsEnabled; }
    public SearchableMultiSelectPreference getSecondaryCitySelection() { return mSecondaryCitySelection; }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof SliderPreference) {
            DialogFragment dialogFragment = SliderPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), null);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
