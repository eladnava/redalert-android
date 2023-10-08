package com.red.alert.logic.push;

import android.content.Context;

public class PushManager {
    public static void updateSubscriptions(Context context) throws Exception {
        // Update FCM subscriptions
        FCMRegistration.updateSubscriptions(context);

        // Update Pushy subscriptions
        PushyRegistration.updateSubscriptions(context);
    }
}
