package com.red.alert.utils.ui;

import android.app.Activity;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.red.alert.R;

public class NavbarUtil {
    public static void fixPreferenceActivityNavbarColor(Activity activity) {
        // Get root content view
        View root = activity.findViewById(android.R.id.content);

        // Set fits system window & background color (so it appears below navbar)
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorPrimaryDark));

        // Set list view background color
        activity.findViewById(android.R.id.list).setBackgroundColor(ContextCompat.getColor(activity, R.color.colorBackground));
    }
}
