package com.red.alert.logic.feedback;

import android.content.Context;
import android.media.AudioManager;

import com.red.alert.R;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.utils.caching.Singleton;

public class VibrationLogic
{
    public static boolean shouldVibrate(String alertType, Context context)
    {
        // Testing sounds?
        if (alertType.equals(AlertTypes.TEST_SOUND) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND))
        {
            return false;
        }

        // Get the audio manager
        AudioManager audioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);

        // Phone is on silent?
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT)
        {
            // Can't override silent mode?
            if (!SoundLogic.shouldOverrideSilentMode(alertType, context))
            {
                return false;
            }
        }

        // All good
        return true;
    }

    public static boolean isVibrationEnabled(String alertType, Context context)
    {
        // Get enabled / disabled setting
        boolean isVibrationEnabled = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.vibratePref), true);

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY))
        {
            isVibrationEnabled = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.secondaryVibratePref), true);
        }

        // Return value
        return isVibrationEnabled;
    }
}
