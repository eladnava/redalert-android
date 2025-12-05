package com.red.alert.ui.elements.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.slider.Slider;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;

import com.red.alert.R;
import com.red.alert.ui.elements.SliderPreference;

public class SliderPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    private Slider mSeekBar;
    private TextView mValueTextView;
    private int mMin, mMax, mDefault;
    private String mUnits;

    public static SliderPreferenceDialogFragmentCompat newInstance(String key) {
        final SliderPreferenceDialogFragmentCompat fragment = new SliderPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @androidx.annotation.NonNull
    @Override
    public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
        final android.content.Context context = requireContext();

        SliderPreference preference = getSliderPreference();
        if (preference == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        final com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                context)
                .setTitle(preference.getDialogTitle())
                .setIcon(preference.getDialogIcon())
                .setPositiveButton(preference.getPositiveButtonText(), this)
                .setNegativeButton(preference.getNegativeButtonText(), this);

        View contentView = onCreateDialogView(context);
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.setView(contentView);
        } else {
            builder.setMessage(preference.getDialogMessage());
        }

        onPrepareDialogBuilder(builder);

        return builder.create();
    }

    private SliderPreference getSliderPreference() {
        // Get key
        String key = getArguments().getString(ARG_KEY);
        DialogPreference preference = null;

        // Try getting it from the target fragment (deprecated but still used)
        if (getTargetFragment() instanceof DialogPreference.TargetFragment) {
            DialogPreference.TargetFragment targetFragment = (DialogPreference.TargetFragment) getTargetFragment();
            preference = targetFragment.findPreference(key);
        }

        // Try getting it from the parent fragment
        if (preference == null && getParentFragment() instanceof PreferenceFragmentCompat) {
            PreferenceFragmentCompat targetFragment = (PreferenceFragmentCompat) getParentFragment();
            preference = targetFragment.findPreference(key);
        }

        // Try getting it from the activity
        if (preference == null && getActivity() instanceof DialogPreference.TargetFragment) {
            DialogPreference.TargetFragment targetFragment = (DialogPreference.TargetFragment) getActivity();
            preference = targetFragment.findPreference(key);
        }

        // Return casted preference
        if (preference instanceof SliderPreference) {
            return (SliderPreference) preference;
        }

        return null;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        // Get the preference
        SliderPreference preference = getSliderPreference();

        // Safety check
        if (preference == null) {
            dismiss();
            return;
        }

        // Get values from preference
        mMin = preference.getMin();
        mMax = preference.getMax();
        mDefault = preference.getSliderDefaultValue();
        mUnits = preference.getUnits();

        // Find views
        mValueTextView = view.findViewById(R.id.message);
        mSeekBar = view.findViewById(R.id.slider_preference_seekbar);

        // Set max value
        // Set value range
        mSeekBar.setValueFrom(mMin);
        mSeekBar.setValueTo(mMax);

        // Get persisted value using public wrapper
        int value = preference.getPersistedValue(mDefault);
        mSeekBar.setValue(value);

        // Set initial value
        updateValue(value);

        // Custom label formatter for percentage
        mSeekBar.setLabelFormatter(new com.google.android.material.slider.LabelFormatter() {
            @androidx.annotation.NonNull
            @Override
            public String getFormattedValue(float value) {
                if (mMax > mMin) {
                    int percentage = (int) ((value - mMin) / (mMax - mMin) * 100);
                    return percentage + "%";
                }
                return String.valueOf((int) value);
            }
        });

        // Add listener
        mSeekBar.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@androidx.annotation.NonNull Slider slider, float value, boolean fromUser) {
                updateValue((int) value);
            }
        });
    }

    private void updateValue(int value) {
        String text = String.valueOf(value);

        // Calculate percentage
        if (mMax > mMin) {
            int percentage = (int) ((float) (value - mMin) / (mMax - mMin) * 100);
            text = percentage + "%";
        }

        if (mUnits != null) {
            text += mUnits;
        }
        if (mValueTextView != null) {
            mValueTextView.setText(text);
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            SliderPreference preference = getSliderPreference();
            if (preference != null) {
                int newValue = (int) mSeekBar.getValue();
                if (preference.callChangeListener(newValue)) {
                    // Persist value using public wrapper
                    preference.setPersistedValue(newValue);
                }
            }
        }
    }
}
