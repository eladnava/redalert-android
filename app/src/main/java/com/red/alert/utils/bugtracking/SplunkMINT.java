package com.red.alert.utils.bugtracking;

import android.content.Context;

import com.red.alert.config.Logging;
import com.splunk.mint.Mint;

public class SplunkMINT {
    public static void logExceptions(Context context) {
        // Initialize bug tracking
        Mint.initAndStartSession(context, Logging.MINT_API_KEY);
    }
}
