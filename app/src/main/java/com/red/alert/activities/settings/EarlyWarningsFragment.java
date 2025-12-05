package com.red.alert.activities.settings;

import android.os.Bundle;
import com.red.alert.R;

public class EarlyWarningsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_early_warnings, rootKey);
    }
}