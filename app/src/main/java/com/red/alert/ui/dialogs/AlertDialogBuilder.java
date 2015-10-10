package com.red.alert.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.ui.localization.rtl.RTLSupport;

public class AlertDialogBuilder
{
    public static final void showGenericDialog(String title, String message, Context context, DialogInterface.OnClickListener clickListener)
    {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Use builder to create dialog
        builder.setTitle(title).setMessage(message).setCancelable(false).setPositiveButton(R.string.okay, clickListener);

        // Prevent cancellation
        builder.setCancelable(false);

        try
        {
            // Build it
            AlertDialog dialog = builder.create();

            // Show it
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, context);
        }
        catch (Exception exc)
        {
            // Show toast instead
            Toast.makeText(context, title + " - " + message, Toast.LENGTH_LONG).show();
        }
    }
}