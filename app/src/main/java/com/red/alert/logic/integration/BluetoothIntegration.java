package com.red.alert.logic.integration;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.red.alert.logic.integration.devices.MiBandIntegration;

public class BluetoothIntegration
{
    public static void notifyDevices(Context context)
    {
        // Check for BLE support + enabled Bluetooth controller
        if ( ! isBLESupported(context) || ! isBluetoothEnabled() )
        {
            // Stop execution
            return;
        }

        // Vibrate + LED for Mi Band (if enabled)
        MiBandIntegration.notifyMiBand(context);
    }

    public static boolean isBLESupported(Context context)
    {
        // Get bluetooth adapter
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Null means device doesn't support Bluetooth (even without BLE)
        if (bluetoothAdapter == null)
        {
            // No Bluetooth support
            return false;
        }

        // Check whether BLE is supported on the device
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            // No BLE support
            return false;
        }

        // Check Android version (BluetoothGatt requires API Level 18+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            return false;
        }

        // We're good
        return true;
    }

    public static boolean isBluetoothEnabled()
    {
        // Get bluetooth adapter
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Null means device doesn't support Bluetooth
        if (bluetoothAdapter == null)
        {
            // No Bluetooth support
            return false;
        }
        else
        {
            // Check if it's enabled and return the result
            return bluetoothAdapter.isEnabled();
        }
    }
}
