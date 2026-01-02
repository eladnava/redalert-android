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
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        
        // Add predictive back animation (Android 13+)
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            // Max corner radius for the shrink effect
            private static final float MAX_CORNER_RADIUS = 64f;
            // Max translation (in dp) to the side
            private static final float MAX_TRANSLATION_DP = 100f;

            private boolean isAtRoot() {
                // Check if we're at the root settings screen (no entries in back stack)
                return getParentFragmentManager().getBackStackEntryCount() == 0;
            }

            @Override
            public void handleOnBackStarted(@NonNull androidx.activity.BackEventCompat backEvent) {
                // Skip animation at root settings screen
                if (isAtRoot()) return;
                
                // Start the predictive back animation
                if (getView() != null) {
                    getView().setClipToOutline(true);
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull androidx.activity.BackEventCompat backEvent) {
                // Skip animation at root settings screen
                if (isAtRoot()) return;
                
                float progress = backEvent.getProgress();
                if (getView() != null) {
                    // Scale down toward center as user swipes (inward shrink effect)
                    float scale = 1f - (progress * 0.1f);
                    getView().setScaleX(scale);
                    getView().setScaleY(scale);

                    // Fade out slightly to hint at previous screen
                    float alpha = 1f - (progress * 0.15f); // Fade to 85% opacity
                    getView().setAlpha(alpha);

                    // Add rounded corners as user swipes
                    float cornerRadius = progress * MAX_CORNER_RADIUS;
                    final float finalCornerRadius = cornerRadius;
                    getView().setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), finalCornerRadius);
                        }
                    });
                    getView().setClipToOutline(true);
                }
            }

            @Override
            public void handleOnBackCancelled() {
                // Skip at root settings screen
                if (isAtRoot()) return;
                
                if (getView() != null) {
                    // Animate back to original position, size, and opacity
                    getView().animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(200)
                        .start();

                    // Reset corner radius
                    getView().setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 0);
                        }
                    });
                    getView().setClipToOutline(true);
                }
            }

            @Override
            public void handleOnBackPressed() {
                // Disable callback to let default behavior happen (popBackStack)
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

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

        // Remove any default bottom padding from RecyclerView
        // The fragment container is already properly constrained above the bottom navigation
        final RecyclerView listView = getListView();
        if (listView != null) {
            listView.setPadding(
                listView.getPaddingLeft(),
                listView.getPaddingTop(),
                listView.getPaddingRight(),
                0  // Remove bottom padding
            );
            listView.setClipToPadding(true);
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