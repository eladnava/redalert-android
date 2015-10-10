package com.red.alert.ui.elements;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.preference.RingtonePreference;
import android.util.AttributeSet;
import android.util.Log;

import com.red.alert.config.Logging;

import java.lang.reflect.Field;

public class RingtoneSelectionPreference extends RingtonePreference
{
    static final int REQUEST_CODE_FAILED = -1000;

    public RingtoneSelectionPreference(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    public RingtoneSelectionPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public RingtoneSelectionPreference(Context context)
    {
        super(context);
    }

    @Override
    protected void onClick()
    {
        // Attempt to get request code
        int requestCode = getRequestCodeViaReflection();

        // Failed? Execute default onClick()
        if (requestCode == REQUEST_CODE_FAILED)
        {
            // Just show our own sounds (don't allow selection from other apps)
            super.onClick();

            // All done
            return;
        }

        // Create ringtone picker intent
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);

        // Prepare it
        onPrepareRingtonePickerIntent(intent);

        // Create chooser activity, this overrides default component
        intent = Intent.createChooser(intent, getTitle());

        // Show intent handler selection
        ((Activity) getContext()).startActivityForResult(intent, requestCode);
    }

    private int getRequestCodeViaReflection()
    {
        try
        {
            // Get request code field by reflection
            Field field = android.preference.RingtonePreference.class.getDeclaredField("mRequestCode");

            // Make it accessible
            field.setAccessible(true);

            // Return it
            return (Integer) field.get(this);
        }
        catch (Exception exc)
        {
            // Log it
            Log.e(Logging.TAG, "Request code reflection failed", exc);

            // Return error
            return REQUEST_CODE_FAILED;
        }
    }
}
