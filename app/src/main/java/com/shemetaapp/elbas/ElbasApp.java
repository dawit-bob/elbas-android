package com.shemetaapp.elbas;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

/** Creates the "Calls" and "Messages" notification channels and starts Firebase (config comes from the ELBAS server). */
public class ElbasApp extends Application {
    public static final String CH_CALLS = "elbas_calls_v1";
    public static final String CH_MSGS = "elbas_msgs_v2";

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
            NotificationChannel m = new NotificationChannel(CH_MSGS, "Messages", NotificationManager.IMPORTANCE_HIGH);
            m.setDescription("Messages from friends and groups");
            m.enableVibration(true);
            m.setVibrationPattern(new long[]{0, 200, 120, 200});
            m.enableLights(true);
            m.setShowBadge(true);
            m.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            getSystemService(NotificationManager.class).createNotificationChannel(m);
            try { getSystemService(NotificationManager.class).deleteNotificationChannel("elbas_msgs_v1"); } catch (Exception ignored) { }   // 1.4's silent channel
        }
        Push.start(this);
    }
}
