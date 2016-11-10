package com.red.alert.services.sound;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.red.alert.config.Logging;
import com.red.alert.logic.communication.intents.SoundServiceParameters;
import com.red.alert.logic.feedback.sound.SoundLogic;
import com.red.alert.logic.feedback.sound.VolumeLogic;
import com.red.alert.utils.feedback.Vibration;

public class PlaySoundService extends Service {
    MediaPlayer mPlayer;
    IBinder mServiceBinder;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize binder
        mServiceBinder = new LocalBinder();

        // Write to log
        Log.d(Logging.TAG, "MediaService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Only if we have an intent
        if (intent != null) {
            // Actually play the sound
            handleIntent(intent);
        }

        // Don't restart this service
        return START_NOT_STICKY;
    }

    void handleIntent(Intent Intent) {
        // Get alert type as extra
        String alertType = Intent.getStringExtra(SoundServiceParameters.ALERT_TYPE);
        String soundResource = Intent.getStringExtra(SoundServiceParameters.ALERT_SOUND);

        // Avoid secondary override
        if (SoundLogic.isSoundCurrentlyPlaying(alertType, this)) {
            return;
        }

        // Can we play it?
        playSound(alertType, soundResource);

        // Vibrate depending on type
        Vibration.issueVibration(alertType, this);
    }

    void playSound(String Type, String Sound) {
        // Should we play it?
        if (SoundLogic.shouldPlayAlertSound(Type, this)) {
            // Get path to resource
            Uri alarmSoundURI = SoundLogic.getAlertSound(Type, Sound, this);

            // Invalid sound URI?
            if (alarmSoundURI == null) {
                return;
            }

            // Override volume (Also to set the user's chosen volume)
            VolumeLogic.setStreamVolume(Type, this);

            // Play sound
            playSoundURI(alarmSoundURI);
        }
    }

    void playSoundURI(Uri alarmSoundURI) {
        // No URI?
        if (alarmSoundURI == null) {
            return;
        }

        // Already initialized or currently playing?
        resetMediaPlayer();

        // Create new MediaPlayer
        mPlayer = new MediaPlayer();

        // Wake up processor
        mPlayer.setWakeMode(PlaySoundService.this, PowerManager.PARTIAL_WAKE_LOCK);

        // Set stream type
        mPlayer.setAudioStreamType(SoundLogic.getSoundStreamType(this));

        try {
            // Set URI data source
            mPlayer.setDataSource(this, alarmSoundURI);

            // Prepare media player
            mPlayer.prepare();
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Media player preparation failed", exc);

            // Show visible error toast
            Toast.makeText(this, exc.toString(), Toast.LENGTH_LONG).show();
        }

        // Actually start playing
        mPlayer.start();
    }

    public void resetMediaPlayer() {
        // Got a player?
        if (mPlayer != null) {
            // Still playing?
            if (mPlayer.isPlaying()) {
                // Stop playing
                mPlayer.stop();

                // Reset media player
                mPlayer.reset();
            }
        }

        // Stop vibration
        Vibration.stopVibration(this);
    }

    @Override
    public void onDestroy() {
        // Release media player
        if (mPlayer != null) {
            mPlayer.release();
        }

        // Now we're ready to be destroyed
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Provide service binder
        return mServiceBinder;
    }

    public class LocalBinder extends Binder {
        public PlaySoundService getService() {
            // Return the instance
            return PlaySoundService.this;
        }
    }
}
