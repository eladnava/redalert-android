package com.red.alert.services.gcm;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.red.alert.config.Logging;
import com.red.alert.logic.push.GCMRegistration;

public class GCMRegistrationService extends IntentService
{
    private static final String TAG = "GCMRegistrationIntentService";

    public GCMRegistrationService()
    {
        // Call super with service class name
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        try
        {
            // Refresh the registration ID
            GCMRegistration.registerForPushNotifications(this);
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Registration failed", exc);
        }
    }
}