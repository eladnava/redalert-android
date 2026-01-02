package com.red.alert.ui.elements;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceViewHolder;

import com.red.alert.R;

public class SliderPreference extends DialogPreference {
    private int mMin, mMax, mSliderDefaultValue;
    private String mUnits;

    public SliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Get custom attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference);
        mMin = a.getInt(R.styleable.SliderPreference_min, 0);
        mMax = a.getInt(R.styleable.SliderPreference_max, 100);
        mSliderDefaultValue = a.getInt(R.styleable.SliderPreference_sliderDefaultValue, 50);
        mUnits = a.getString(R.styleable.SliderPreference_units);
        a.recycle();

        // Set layout
        setDialogLayoutResource(R.layout.slider_preference_dialog);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(true);
    }

    public int getMin() {
        return mMin;
    }

    public int getMax() {
        return mMax;
    }

    public int getSliderDefaultValue() {
        return mSliderDefaultValue;
    }

    public String getUnits() {
        return mUnits;
    }

    // Public wrapper for protected getPersistedInt
    public int getPersistedValue(int defaultValue) {
        return getPersistedInt(defaultValue);
    }

    // Public wrapper for protected persistInt
    public void setPersistedValue(int value) {
        if (shouldPersist()) {
            persistInt(value);
        }
    }
}