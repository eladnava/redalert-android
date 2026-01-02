package com.red.alert.activities.settings;

import android.os.Bundle;
import com.red.alert.R;

public class LocationAlertsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_location_alerts, rootKey);
    }
}