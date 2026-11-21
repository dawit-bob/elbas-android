package com.shemetaapp.elbas;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

/** Creates the "Calls" notification channel and starts Firebase (config comes from the ELBAS server). */
public class ElbasApp extends Application {
    public static final String CH_CALLS = "elbas_calls_v1";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Incoming ELBAS calls");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Push.start(this);
    }
}
