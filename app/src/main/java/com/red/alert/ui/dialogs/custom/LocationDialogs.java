package com.red.alert.ui.dialogs.custom;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.logic.settings.AppPreferences;
import com.red.alert.ui.localization.rtl.RTLSupport;

public class LocationDialogs
{
    private static AlertDialog mLocationServicesDialog;

    public static void requestEnableLocationServices(final Context context)
    {
        // Location alerts disabled?
        if (!AppPreferences.getLocationAlertsEnabled(context))
        {
            return;
        }

        // Get location manager
        LocationManager locationManager = (LocationManager) context.getSystemService(context.LOCATION_SERVICE);

        // GPS enabled?
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            return;
        }

        // Network triangulation enabled?
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        {
            return;
        }

        // Already have a historical dialog?
        if ( mLocationServicesDialog != null )
        {
            try
            {
                // Try to dismiss it
                mLocationServicesDialog.hide();
            }
            catch (Exception exc)
            {
                // The dialog is probably no longer valid
                mLocationServicesDialog = null;
            }
        }

        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Use builder to create dialog
        builder.setTitle(context.getString(R.string.locationAlerts)).setMessage(context.getString(R.string.enableGPSDesc));

        // Set positive button
        builder.setPositiveButton(R.string.enable, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                // Display the GPS setting screen
                context.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });

        // Set negative button
        builder.setNegativeButton(R.string.notNow, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int i)
            {
                // Hide dialog
                dialog.dismiss();
            }
        });

        try
        {
            // Create the dialog
            mLocationServicesDialog = builder.create();

            // Show dialog
            mLocationServicesDialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(mLocationServicesDialog, context);

        }
        catch (Exception exc)
        {
            // Show toast instead
            Toast.makeText(context, context.getString(R.string.enableGPSDesc), Toast.LENGTH_LONG).show();
        }
    }
}
