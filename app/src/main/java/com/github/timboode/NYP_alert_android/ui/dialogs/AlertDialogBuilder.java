package com.github.timboode.NYP_alert_android.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.ui.localization.rtl.RTLSupport;
import com.github.timboode.NYP_alert_android.utils.formatting.StringUtils;

public class AlertDialogBuilder {
    public static final void showGenericDialog(String title, String message, String positiveButton, String negativeButton, boolean cancelable, Context context, DialogInterface.OnClickListener clickListener) {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

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
            AlertDialog dialog = builder.create();

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