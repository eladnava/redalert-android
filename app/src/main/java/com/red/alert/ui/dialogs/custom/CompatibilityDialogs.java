package com.red.alert.ui.dialogs.custom;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.compatibility.DozeCompatibility;

public class CompatibilityDialogs
{
    public static void showDozeDialog(final Context context)
    {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Prepare dialog title and message
        String title = context.getString(R.string.doze);
        String message = context.getString(R.string.dozeDesc);

        // Use builder to create dialog
        builder.setTitle(title).setMessage(message);

        // Set positive button
        builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                // Ask user to disable battery optimizations
                DozeCompatibility.requestDisableBatteryOptimizations(context);
            }
        });

        // Set negative button
        builder.setNegativeButton(R.string.notNow, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface Dialog, int i)
            {
                // Close dialog
                Dialog.dismiss();
            }
        });

        try
        {
            // Create the dialog
            AlertDialog dialog = builder.create();

            // Show dialog
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, context);
        }
        catch (Exception exc)
        {
            // Show toast instead
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
