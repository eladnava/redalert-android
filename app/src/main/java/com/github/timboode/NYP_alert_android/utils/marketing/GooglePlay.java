package com.github.timboode.NYP_alert_android.utils.marketing;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.github.timboode.NYP_alert_android.config.Logging;

public class GooglePlay {
    public static void openAppListingPage(Context context) {
        // Initialize Google Play intent
        Intent rateAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getApplicationContext().getPackageName()));

        // Is Google Play installed?
        if (context.getPackageManager().queryIntentActivities(rateAppIntent, 0).size() > 0) {
            try {
                // Try to open Google Play and navigate to app page
                context.startActivity(rateAppIntent);
            }
            catch (Exception exc) {
                // Log it
                Log.e(Logging.TAG, "Rate activity launch failed", exc);
            }
        }
        else {
            // Log it
            Log.e(Logging.TAG, "Can't rate. Google Play app not installed");
        }
    }
}
