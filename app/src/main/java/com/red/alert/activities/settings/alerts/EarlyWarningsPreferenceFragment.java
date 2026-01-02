package com.red.alert.activities.settings.alerts;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.red.alert.R;

public class EarlyWarningsPreferenceFragment extends PreferenceFragmentCompat {
    private EarlyWarnings mActivity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof EarlyWarnings) {
            mActivity = (EarlyWarnings) context;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_early_warnings, rootKey);

        // Notify activity that preferences are ready
        if (mActivity != null) {
            mActivity.onFragmentPreferencesReady();
        }
    }
}
