package com.red.alert.ui.localization.rtl;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.red.alert.utils.localization.Localization;

public class RTLSupport {
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static void mirrorActionBar(Activity activity) {
        // Hebrew only
        if (!Localization.isHebrewLocale(activity)) {
            return;
        }

        // Must be Jellybean or newer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Set RTL layout direction
            activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
    }

    public static void mirrorDialog(Dialog dialog, Context context) {
        // Hebrew only
        if (!Localization.isHebrewLocale(context)) {
            return;
        }

        try {
            // Get message text view
            TextView message = (TextView) dialog.findViewById(android.R.id.message);

            // Defy gravity
            message.setGravity(Gravity.RIGHT);

            // Get the title of text view
            TextView title = (TextView) dialog.findViewById(context.getResources().getIdentifier("alertTitle", "id", "android"));

            // Defy gravity
            title.setGravity(Gravity.RIGHT);

            // Get title's parent layout
            LinearLayout parent = ((LinearLayout) title.getParent());

            // Get layout params
            LinearLayout.LayoutParams originalParams = (LinearLayout.LayoutParams) parent.getLayoutParams();

            // Set width to WRAP_CONTENT
            originalParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;

            // Defy gravity
            originalParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;

            // Set layout params
            parent.setLayoutParams(originalParams);
        }
        catch (Exception exc) {
            // Ignore, this is completely optional behavior
        }
    }
}
