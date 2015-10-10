package com.red.alert.services.sound;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class StopSoundService extends Service
{
    @Override
    public int onStartCommand(Intent Intent, int Flags, int StartId)
    {
        // Stop currently playing sounds
        stopSoundService(this);

        // Don't restart this service
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0)
    {
        // Provide service binder
        return null;
    }

    public static void stopSoundService(final Context context)
    {
        // Bind to the sound service
        context.bindService(new Intent(context, PlaySoundService.class), new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder)
            {
                // Convert binder to LocalBinder
                PlaySoundService.LocalBinder localBinder = (PlaySoundService.LocalBinder) binder;

                // Get service instance
                PlaySoundService service = localBinder.getService();

                // Stop sound
                service.resetMediaPlayer();

                // Unbind fom service
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName)
            {
                // Do nothing
            }
        }, context.BIND_AUTO_CREATE);
    }
}
