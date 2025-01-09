package com.github.timboode.NYP_alert_android.ui.compatibility;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;

import com.github.timboode.NYP_alert_android.R;

public class ProgressDialogCompat {
    public static ProgressDialog getStyledProgressDialog(Context context) {
        // Only for pre-Honeycomb devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return new ProgressDialog(context, R.style.ProgressDialogCompat);
        }

        // Return a basic ProgressDialog
        return new ProgressDialog(context);
    }
}
