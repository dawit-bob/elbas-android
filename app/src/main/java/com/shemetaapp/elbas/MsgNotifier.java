package com.shemetaapp.elbas;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * New chat message from a friend or a group.
 * App open: the page shows its own banner with a tone (no double alert). App closed or in the background:
 * a heads-up notification with sound and vibration; several messages in one chat stack in one notification.
 * Either way ELBAS is told the message reached this phone ("delivered" ✓✓).
 */
public final class MsgNotifier {
    private MsgNotifier() {}
    private static final Map<String, List<String>> LINES = new HashMap<>();

    public static void show(Context c, String thread, String title, String from, String text, String id, String group) {
        if (thread == null || !thread.matches("[a-f0-9]{24}")) return;
        if (id != null && id.matches("[0-9]{1,18}")) Push.delivered(c, thread, id);
        if (MainActivity.visible) { MainActivity.alertsCheck(); return; }   // on screen: the page shows the banner and plays the tone
        if (title == null || title.isEmpty()) title = "ELBAS";
        if (text == null) text = "";
        boolean grp = "1".equals(group);
        String line = text;   // ELBAS already puts the sender's name in front for groups
        List<String> lines;
        synchronized (LINES) {
            lines = LINES.get(thread); if (lines == null) { lines = new ArrayList<>(); LINES.put(thread, lines); }
            lines.add(line); while (lines.size() > 6) lines.remove(0);
            lines = new ArrayList<>(lines);
        }
        Intent open = new Intent(c, MainActivity.class).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(BuildConfig.SITE + "/messages?t=" + thread))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, thread.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ElbasApp.CH_MSGS) : new Notification.Builder(c);
        String head = (grp ? "👥 " : "💬 ") + title;
        b.setSmallIcon(R.drawable.ic_call).setContentTitle(head).setContentText(line)
            .setAutoCancel(true).setContentIntent(pi).setCategory(Notification.CATEGORY_MESSAGE).setShowWhen(true).setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(false).setVisibility(Notification.VISIBILITY_PRIVATE);
        if (lines.size() > 1) {
            Notification.InboxStyle st = new Notification.InboxStyle().setBigContentTitle(head);
            for (String l : lines) st.addLine(l);
            st.setSummaryText(lines.size() + " ✉");
            b.setStyle(st).setNumber(lines.size());
        } else b.setStyle(new Notification.BigTextStyle().bigText(line));
        if (Build.VERSION.SDK_INT < 26) { b.setPriority(Notification.PRIORITY_HIGH); b.setDefaults(Notification.DEFAULT_ALL); }
        c.getSystemService(NotificationManager.class).notify(thread.hashCode(), b.build());
    }

    /** The chat was opened: clear its notification and its stacked lines. */
    public static void clear(Context c, String thread) {
        if (thread == null) return;
        synchronized (LINES) { LINES.remove(thread); }
        try { c.getSystemService(NotificationManager.class).cancel(thread.hashCode()); } catch (Exception ignored) { }
    }
}
