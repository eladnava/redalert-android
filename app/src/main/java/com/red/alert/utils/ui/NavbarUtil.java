package com.red.alert.utils.ui;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.red.alert.R;

public class NavbarUtil {
    public static void fixPreferenceActivityNavbarColor(Activity activity) {
        // Get root content view
        View root = activity.findViewById(android.R.id.content);

        // Set fits system window & background color (so it appears below navbar)
        root.setFitsSystemWindows(true);

        // Fix navbar overlay in landscape mode (Android 15)
        if (Build.VERSION.SDK_INT == 35) {
            // Wait for insets
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                // Get system bar insets
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                // Apply padding for all sides (handles landscape mode)
                v.setPadding(
                        bars.left,
                        bars.top,
                        bars.right,
                        bars.bottom
                );

                // Override default insets
                return insets;
            });
        }

        // Override background color
        root.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorPrimaryDark));

        // Override list view background color
        activity.findViewById(android.R.id.list).setBackgroundColor(ContextCompat.getColor(activity, R.color.colorBackground));
    }
}
