package com.shemetaapp.elbas;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** ELBAS published a new app version: tell the person even if the app is closed; tapping opens the update question. */
final class UpdateNotifier {
    private UpdateNotifier() {}

    static void show(Context c, String version, String title, String text) {
        if (version == null || !MainActivity.newer(version, BuildConfig.VERSION_NAME)) return;   // already up to date
        if (MainActivity.visible) { MainActivity.updateNow(); return; }
        if (title == null || title.isEmpty()) title = "Update available";
        if (text == null || text.isEmpty()) text = "ELBAS AI " + version + " is ready. Tap to update.";
        Intent open = new Intent(c, MainActivity.class).putExtra("update", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 9090, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ElbasApp.CH_UPDATES) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_call).setContentTitle("⬆ " + title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(pi).setShowWhen(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT);
        c.getSystemService(NotificationManager.class).notify(9090, b.build());
    }
}
