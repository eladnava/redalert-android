package com.red.alert.activities.settings;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.red.alert.R;
import com.red.alert.activities.Main;
import com.red.alert.logic.communication.broadcasts.SettingsEvents;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.localization.Localization;

import androidx.preference.Preference;
import androidx.fragment.app.DialogFragment;
import com.red.alert.ui.elements.SliderPreference;
import com.red.alert.ui.elements.dialogs.SliderPreferenceDialogFragmentCompat;

public class BasePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Transitions are handled by FragmentTransaction animations in Main.navigateToFragment
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Override in child
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set background to surface color to prevent transparency during transitions
        view.setBackgroundResource(R.color.md3_surface);

        // Add padding to RecyclerView to avoid overlap with Bottom Navigation
        final RecyclerView listView = getListView();
        if (listView != null) {
            listView.setClipToPadding(false);
            // Convert 150dp to pixels for consistent padding across devices
            int paddingPx = (int) (150 * getResources().getDisplayMetrics().density);
            listView.setPadding(0, 0, 0, paddingPx);
        }
    }

    protected boolean shouldUpdateTitleInOnResume() {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register listener
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Update title when resuming (needed for back navigation)
        if (shouldUpdateTitleInOnResume() && getActivity() instanceof Main && getPreferenceScreen() != null) {
            // Main.updateTitle will handle the capitalization and animation
            ((Main) getActivity()).updateTitle(getPreferenceScreen().getTitle());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister listener
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Generic handler if needed
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // Try casting the preference to the custom preference
        if (preference instanceof SliderPreference) {
            // Create a new instance of SliderPreferenceDialogFragment with a key
            DialogFragment dialogFragment = SliderPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), null);
        } else if (preference instanceof androidx.preference.ListPreference) {
            // Use Material 3 ListPreference dialog
            DialogFragment dialogFragment = com.red.alert.ui.elements.dialogs.MaterialListPreferenceDialogFragmentCompat
                    .newInstance(preference.getKey());
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), null);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    protected void restartActivity() {
        if (getActivity() != null) {
            // Notify theme/language changed
            Broadcasts.publish(getContext(), SettingsEvents.THEME_OR_LANGUAGE_CHANGED);
        }
    }
}