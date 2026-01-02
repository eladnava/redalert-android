package com.red.alert.ui.elements.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MaterialListPreferenceDialogFragmentCompat extends ListPreferenceDialogFragmentCompat {

    public static MaterialListPreferenceDialogFragmentCompat newInstance(String key) {
        final MaterialListPreferenceDialogFragmentCompat fragment = new MaterialListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Context context = requireContext();
        // mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE; // Private access,
        // rely on default

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(getPreference().getDialogTitle())
                .setPositiveButton(getPreference().getPositiveButtonText(), this)
                .setNegativeButton(getPreference().getNegativeButtonText(), this);

        View contentView = onCreateDialogView(context);
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.setView(contentView);
        } else {
            builder.setMessage(getPreference().getDialogMessage());
        }

        onPrepareDialogBuilder(builder);

        // Create the dialog
        Dialog dialog = builder.create();
        if (needInputMethod()) {
            requestInputMethod(dialog);
        }
        return dialog;
    }

    private void requestInputMethod(Dialog dialog) {
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
}
