package com.myservyfast.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver to handle boot completed events.
 * Used to maintain app state and sessions after device restart.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // Sessions and cookies are maintained in WebView
            // No action needed - app will restore sessions on launch
        }
    }
}
