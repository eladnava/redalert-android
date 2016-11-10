package com.red.alert.services.gcm;

import android.content.Intent;

import com.google.android.gms.iid.InstanceIDListenerService;

public class GCMInstanceIDService extends InstanceIDListenerService {
    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. This call is initiated by the
     * InstanceID provider.
     */

    @Override
    public void onTokenRefresh() {
        // Call the intent service to re-register the device
        Intent intent = new Intent(this, GCMRegistrationService.class);

        // Start service (since this may take a while)
        startService(intent);
    }
}
