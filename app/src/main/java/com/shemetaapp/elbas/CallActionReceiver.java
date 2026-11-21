package com.shemetaapp.elbas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Decline from the call notification (Answer opens the app directly). */
public class CallActionReceiver extends BroadcastReceiver {
    static final String ANSWER = "com.shemetaapp.elbas.ANSWER";
    static final String DECLINE = "com.shemetaapp.elbas.DECLINE";

    @Override public void onReceive(Context c, Intent i) {
        String tok = i.getStringExtra("token");
        if (tok == null) return;
        CallNotifier.stop(c, tok);
        if (DECLINE.equals(i.getAction())) Push.decline(c, tok);
    }
}
