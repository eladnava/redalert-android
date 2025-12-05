package com.red.alert.ui.elements;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.red.alert.R;

public class MaterialProgressDialog {
    private AlertDialog mDialog;
    private TextView mMessageView;

    public MaterialProgressDialog(Context context) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        mMessageView = view.findViewById(R.id.message);
        builder.setView(view);
        builder.setCancelable(false);
        mDialog = builder.create();
    }

    public void setMessage(String message) {
        if (mMessageView != null) {
            mMessageView.setText(message);
        }
    }

    public void setCancelable(boolean cancelable) {
        mDialog.setCancelable(cancelable);
    }

    public void show() {
        mDialog.show();
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    public boolean isShowing() {
        return mDialog.isShowing();
    }
}
