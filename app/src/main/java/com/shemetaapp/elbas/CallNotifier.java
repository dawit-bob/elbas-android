package com.shemetaapp.elbas;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Shows the incoming call: full-screen call screen (even when the app is closed or the phone is locked). */
public final class CallNotifier {
    static final int NOTIF_ID = 4747;
    static final String ACTION_STOP = "com.shemetaapp.elbas.STOP_RING";
    private CallNotifier() {}

    static PendingIntent action(Context c, String act, String tok, int req) {
        Intent i = new Intent(c, CallActionReceiver.class).setAction(act).putExtra("token", tok);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void ring(Context c, String tok, String caller, String business) {
        if (caller == null || caller.isEmpty()) caller = "ELBAS";
        if (business == null) business = "";
        Intent full = new Intent(c, IncomingCallActivity.class)
            .putExtra("token", tok).putExtra("caller", caller).putExtra("business", business)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(c, 1, full, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Answer opens the app straight into the call (an activity PendingIntent: Android 12+ blocks starting it from a receiver).
        PendingIntent answer = PendingIntent.getActivity(c, 2, callIntent(c, tok), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent decline = action(c, CallActionReceiver.DECLINE, tok, 3);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ElbasApp.CH_CALLS) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_call).setContentTitle(caller).setContentText(business.isEmpty() ? "Incoming ELBAS call" : business)
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setAutoCancel(false)
            .setFullScreenIntent(fullPi, true).setContentIntent(fullPi).setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(60000);
        if (Build.VERSION.SDK_INT < 26) { b.setPriority(Notification.PRIORITY_MAX); b.setVibrate(new long[]{0, 800, 400, 800}); }
        if (Build.VERSION.SDK_INT >= 31) {
            Person p = new Person.Builder().setName(caller).setImportant(true).build();
            b.setStyle(Notification.CallStyle.forIncomingCall(p, decline, answer));
        } else {
            b.addAction(new Notification.Action.Builder(null, "Decline", decline).build());
            b.addAction(new Notification.Action.Builder(null, "Answer", answer).build());
        }
        c.getSystemService(NotificationManager.class).notify(NOTIF_ID, b.build());
        // Also open the call screen directly when the system allows it (screen off / app in front).
        try { c.startActivity(full); } catch (Exception ignored) { }
    }

    public static void stop(Context c, String tok) {
        c.getSystemService(NotificationManager.class).cancel(NOTIF_ID);
        c.sendBroadcast(new Intent(ACTION_STOP).setPackage(c.getPackageName()).putExtra("token", tok));
    }

    static Intent callIntent(Context c, String tok) {
        return new Intent(c, MainActivity.class).setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse(BuildConfig.SITE + "/calls?t=" + tok + "&go=1"))
            .putExtra("answer", tok)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    static void openCall(Context c, String tok) { c.startActivity(callIntent(c, tok)); }
}
