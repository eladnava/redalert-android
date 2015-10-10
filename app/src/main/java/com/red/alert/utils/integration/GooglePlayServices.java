package com.red.alert.utils.integration;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.red.alert.R;

public class GooglePlayServices
{
    public static boolean isAvailable(Context context)
    {
        // Get availability checker
        GoogleApiAvailability playServices = GoogleApiAvailability.getInstance();

        // Check whether services are available
        int result = playServices.isGooglePlayServicesAvailable(context);

        // Are we good?
        return result == ConnectionResult.SUCCESS;
    }
}
