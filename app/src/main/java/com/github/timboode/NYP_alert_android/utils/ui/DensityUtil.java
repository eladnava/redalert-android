package com.github.timboode.NYP_alert_android.utils.ui;

import android.content.Context;

public class DensityUtil {
    public static int convertDPToPixels(Context context, int dps) {
        // Get scale
        float scale = context.getResources().getDisplayMetrics().density;

        // Calculate pixels
        return (int) (dps * scale + 0.5f);
    }
}
