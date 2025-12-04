package com.red.alert.ui.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.red.alert.ui.localization.rtl.RTLSupport;
import com.red.alert.utils.formatting.StringUtils;

public class AlertDialogBuilder {
    public static final void showGenericDialog(String title, String message, String positiveButton, String negativeButton, boolean cancelable, Context context, DialogInterface.OnClickListener clickListener) {
        // Use Material 3 dialog builder
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        // Use builder to create dialog
        builder.setTitle(title).setMessage(message).setPositiveButton(positiveButton, clickListener);

        // Negative button defined?
        if (!StringUtils.stringIsNullOrEmpty(negativeButton)) {
            builder.setNegativeButton(negativeButton, clickListener);
        }

        // Set cancelable flag
        builder.setCancelable(cancelable);

        try {
            // Build it
            androidx.appcompat.app.AlertDialog dialog = builder.create();

            // Show it
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, context);
        }
        catch (Exception exc) {
            // Show toast instead
            Toast.makeText(context, title + " - " + message, Toast.LENGTH_LONG).show();
        }
    }
}
