package com.github.timboode.NYP_alert_android.ui.dialogs.custom;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.utils.marketing.GooglePlay;

public class UpdateDialogs {
    public static void showUpdateDialog(final Context context, String newVersion) {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Insert version into message
        String message = String.format(context.getString(R.string.updateDesc), newVersion);

        // Use builder to create dialog
        builder.setTitle(context.getString(R.string.update)).setMessage(message);

        // Set positive button
        builder.setPositiveButton(R.string.updatePositive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Open app page
                GooglePlay.openAppListingPage(context);
            }
        });

        // Set negative button
        builder.setNegativeButton(R.string.notNow, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Close dialog
                dialogInterface.dismiss();
            }
        });

        try {
            // Create the dialog
            AlertDialog dialog = builder.create();

            // Show dialog
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, context);
        }
        catch (Exception exc) {
            // Show toast instead
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
