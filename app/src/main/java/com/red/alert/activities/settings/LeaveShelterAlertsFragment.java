package com.red.alert.activities.settings;

import android.os.Bundle;
import com.red.alert.R;

public class LeaveShelterAlertsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_leave_shelter_alerts, rootKey);
    }
}