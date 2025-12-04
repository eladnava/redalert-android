package com.red.alert.activities.settings.alerts;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.red.alert.R;

public class LeaveShelterAlertsPreferenceFragment extends PreferenceFragmentCompat {
    private LeaveShelterAlerts mActivity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof LeaveShelterAlerts) {
            mActivity = (LeaveShelterAlerts) context;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_leave_shelter_alerts, rootKey);

        // Notify activity that preferences are ready
        if (mActivity != null) {
            mActivity.onFragmentPreferencesReady();
        }
    }
}
