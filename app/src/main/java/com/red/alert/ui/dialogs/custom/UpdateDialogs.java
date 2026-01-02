package com.red.alert.ui.dialogs.custom;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.red.alert.R;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.marketing.GooglePlay;

public class UpdateDialogs {
    public static void showUpdateDialog(final Context context, String newVersion) {
        // Use Material 3 dialog builder
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

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
            androidx.appcompat.app.AlertDialog dialog = builder.create();

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
