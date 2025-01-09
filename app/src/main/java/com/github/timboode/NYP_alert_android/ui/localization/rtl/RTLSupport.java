package com.github.timboode.NYP_alert_android.ui.localization.rtl;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.timboode.NYP_alert_android.config.Logging;
import com.github.timboode.NYP_alert_android.utils.localization.Localization;

public class RTLSupport {
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static void mirrorActionBar(Activity activity) {
        // Must be Jellybean or newer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Set RTL layout direction
            activity.getWindow().getDecorView().setLayoutDirection(Localization.isRTLLocale(activity) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }
    }

    @SuppressLint("NewApi")
    public static void mirrorDialog(Dialog dialog, Context context) {
        // RTL locale only
        if (!Localization.isRTLLocale(context)) {
            return;
        }

        try {
            // Get message text view
            TextView message = (TextView) dialog.findViewById(android.R.id.message);

            // Defy gravity
            if (message != null) {
                message.setGravity(Gravity.RIGHT);
            }

            // Get the title of text view
            TextView title = (TextView) dialog.findViewById(context.getResources().getIdentifier("alertTitle", "id", "android"));

            // Defy gravity
            title.setGravity(Gravity.RIGHT);

            // Get list view (may not exist)
            ListView listView = (ListView) dialog.findViewById(context.getResources().getIdentifier("select_dialog_listview", "id", "android"));

            // Check if list & set RTL mode
            if (listView != null) {
                listView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            }

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
            // Log failure to logcat
            Log.d(Logging.TAG, "RTL failed", exc);
        }
    }
}
