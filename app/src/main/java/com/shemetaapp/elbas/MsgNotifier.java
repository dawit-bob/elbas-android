package com.shemetaapp.elbas;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

/** New chat message from a friend or a group: a normal notification that opens the chat. */
public final class MsgNotifier {
    private MsgNotifier() {}

    public static void show(Context c, String thread, String title, String from, String text) {
        if (thread == null || !thread.matches("[a-f0-9]{24}")) return;
        if (MainActivity.visible && MainActivity.currentUrl != null && MainActivity.currentUrl.contains("t=" + thread)) return;   // already reading it
        if (title == null || title.isEmpty()) title = "ELBAS";
        if (text == null) text = "";
        String body = (from != null && !from.isEmpty() && !from.equals(title) ? from + ": " : "") + text;
        Intent open = new Intent(c, MainActivity.class).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(BuildConfig.SITE + "/messages?t=" + thread))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, thread.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ElbasApp.CH_MSGS) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_call).setContentTitle("💬 " + title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(pi).setCategory(Notification.CATEGORY_MESSAGE).setShowWhen(true).setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT < 26) { b.setPriority(Notification.PRIORITY_HIGH); b.setDefaults(Notification.DEFAULT_ALL); }
        c.getSystemService(NotificationManager.class).notify(thread.hashCode(), b.build());
    }
}
