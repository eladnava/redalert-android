package com.red.alert.ui.dialogs.custom;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import com.red.alert.R;
import com.red.alert.ui.localization.rtl.RTLSupport;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BluetoothDialogs {
    private static AlertDialog mEnableBluetoothDialog;

    public static void showEnableBluetoothDialog(final Activity activity) {
        // Android M (6) and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Already have a historical dialog?
            if (mEnableBluetoothDialog != null) {
                try {
                    // Try to dismiss it
                    mEnableBluetoothDialog.hide();
                } catch (Exception exc) {
                    // The dialog is probably no longer valid
                    mEnableBluetoothDialog = null;
                }
            }

            // Use builder to create dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            // Use builder to create dialog
            builder.setTitle(activity.getString(R.string.enableBluetooth)).setMessage(activity.getString(R.string.enableBluetoothDesc));

            // Set positive button
            builder.setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // Ask user to enable it via built-in Android dialog
                    activity.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                }
            });

            // Set negative button
            builder.setNegativeButton(R.string.notNow, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    // Hide dialog
                    dialog.dismiss();
                }
            });

            try {
                // Create the dialog
                mEnableBluetoothDialog = builder.create();

                // Show dialog
                mEnableBluetoothDialog.show();

                // Support for RTL languages
                RTLSupport.mirrorDialog(mEnableBluetoothDialog, activity);
            } catch (Exception exc) {
                // Show toast instead
                Toast.makeText(activity, activity.getString(R.string.enableBluetoothDesc), Toast.LENGTH_LONG).show();
            }
        }
        else {
            // Runtime permissions required for Bluetooth access to Mi Band
            Set<String> permissions = new HashSet<>(Arrays.asList(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ));

            // Android 13 and up also requires the BLUETOOTH_CONNECT and BLUETOOTH_SCAN runtime permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }

            // Show permission request dialog
            activity.requestPermissions(permissions.toArray(new String[0]),600);
        }
    }

    public static void showBLENotSupportedDialog(final Activity activity) {
        // Use builder to create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Use builder to create dialog
        builder.setTitle(activity.getString(R.string.bleNotSupported)).setCancelable(false).setMessage(activity.getString(R.string.bleNotSupportedDesc));

        // Set positive button
        builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Dismiss dialog
                dialogInterface.dismiss();

                // Still alive?
                if (!activity.isFinishing()) {
                    // Close the activity
                    activity.finish();
                }
            }
        });

        try {
            // Create the dialog
            AlertDialog dialog = builder.create();

            // Show dialog
            dialog.show();

            // Support for RTL languages
            RTLSupport.mirrorDialog(dialog, activity);

        }
        catch (Exception exc) {
            // Show toast instead
            Toast.makeText(activity, activity.getString(R.string.bleNotSupportedDesc), Toast.LENGTH_LONG).show();
        }
    }
}
