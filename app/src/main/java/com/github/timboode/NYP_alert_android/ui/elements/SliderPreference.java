package com.github.timboode.NYP_alert_android.ui.elements;

/*
 * Copyright 2012 Jay Weisskopf
 *
 * Licensed under the MIT License (see LICENSE.txt)
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.utils.localization.Localization;

/**
 * @author Jay Weisskopf
 */
public class SliderPreference extends DialogPreference {

    protected final static int SEEKBAR_RESOLUTION = 10000;

    protected float mValue;
    protected float minValue;
    protected int mSeekBarValue;
    protected CharSequence[] mSummaries;
    onSeekBarChangedListener seekBarChangedListener;

    /**
     * @param context
     * @param attrs
     */
    public SliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context, attrs);
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public SliderPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setup(context, attrs);
    }

    private void setup(Context context, AttributeSet attrs) {
        setDialogLayoutResource(R.layout.slider_preference_dialog);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference);
        try {
            setSummary(a.getTextArray(R.styleable.SliderPreference_android_summary));
        }
        catch (Exception e) {
            // Do nothing
        }

        try {
            minValue = a.getFloat(R.styleable.SliderPreference_android_min, 0);
        }
        catch (Exception e) {
            // Do nothing
        }

        a.recycle();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getFloat(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedFloat(mValue) : (Float) defaultValue);
    }

    @Override
    public CharSequence getSummary() {
        if (mSummaries != null && mSummaries.length > 0) {
            int index = (int) (mValue * mSummaries.length);
            index = Math.min(index, mSummaries.length - 1);
            return mSummaries[index];
        }
        else {
            return super.getSummary();
        }
    }

    @Override
    @SuppressWarnings("ResourceType")
    public void setSummary(int summaryResId) {
        try {
            setSummary(getContext().getResources().getStringArray(summaryResId));
        }
        catch (Exception e) {
            super.setSummary(summaryResId);
        }
    }

    public void setSummary(CharSequence[] summaries) {
        mSummaries = summaries;
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(summary);
        mSummaries = null;
    }

    public float getValue() {
        return mValue;
    }

    public void setValue(float value) {
        value = Math.max(0, Math.min(value, 1)); // clamp to [0, 1]
        if (shouldPersist()) {
            persistFloat(value);
        }
        if (value != mValue) {
            mValue = value;
            notifyChanged();
        }
    }

    onSeekBarChangedListener getSeekBarChangedListener() {
        return seekBarChangedListener;
    }

    public void setSeekBarChangedListener(onSeekBarChangedListener listener) {
        seekBarChangedListener = listener;
    }

    public String getDialogMessage(float value) {
        if (getSeekBarChangedListener() == null) {
            return "?";
        }

        return Localization.localizeDigits(getSeekBarChangedListener().getDialogMessage(value), getContext());
    }

    @Override
    protected View onCreateDialogView() {
        mSeekBarValue = (int) (mValue * SEEKBAR_RESOLUTION);
        View view = super.onCreateDialogView();

        final TextView message = (TextView) view.findViewById(android.R.id.message);
        final SeekBar seekbar = (SeekBar) view.findViewById(R.id.slider_preference_seekbar);

        seekbar.setMax(SEEKBAR_RESOLUTION);
        seekbar.setProgress(mSeekBarValue);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Check if minimum allowed value is set for this SliderPreference
                if (minValue > 0) {
                    // Calculate min value allowed
                    int minAllowed = (int) (minValue * SEEKBAR_RESOLUTION);

                    // Progress bar set too low?
                    if (progress < minAllowed) {
                        // Increase progress to minimum value allowed
                        progress = minAllowed;
                        seekBar.setProgress(progress);
                    }
                }

                // Update progress value and refresh dialog summary text
                SliderPreference.this.mSeekBarValue = progress;
                reloadDialogSummary(message);
            }
        });

        // Set initial summary
        reloadDialogSummary(message);

        return view;
    }

    void reloadDialogSummary(TextView message) {
        float newValue = (float) mSeekBarValue / SEEKBAR_RESOLUTION;

        String newMessage = getDialogMessage(newValue);

        message.setText(newMessage);
        setDialogMessage(newMessage);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        final float newValue = (float) mSeekBarValue / SEEKBAR_RESOLUTION;
        if (positiveResult && callChangeListener(newValue)) {
            setValue(newValue);
        }
        super.onDialogClosed(positiveResult);
    }

    public interface onSeekBarChangedListener {
        String getDialogMessage(float value);
    }

    // TODO: Save and restore preference state.
}
