package com.shemetaapp.elbas;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Downloads the new ELBAS APK inside the app and opens Android's installer (the person taps Install).
 * First time only, Android asks to allow "Install unknown apps" for ELBAS. Anything fails: open the download in the browser.
 */
final class Updater {
    private Updater() {}
    private static long downloadId = -1;
    private static String pendingApk = null, pendingVersion = null;
    private static boolean waitingPermission = false;

    static void start(Activity a, String apk, String version) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
                pendingApk = apk; pendingVersion = version; waitingPermission = true;
                Toast.makeText(a, a.getString(R.string.upd_allow), Toast.LENGTH_LONG).show();
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                return;
            }
            download(a, apk, version);
        } catch (Exception e) { browser(a, apk); }
    }

    /** Back from the "Install unknown apps" setting: carry on with the download. */
    static void resume(Activity a) {
        if (!waitingPermission || pendingApk == null) return;
        waitingPermission = false;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) { browser(a, pendingApk); return; }
        download(a, pendingApk, pendingVersion);
    }

    private static void download(Activity a, String apk, String version) {
        try {
            Context app = a.getApplicationContext();
            DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            String name = "elbas-ai-" + (version == null ? "new" : version.replaceAll("[^0-9.]", "")) + ".apk";
            java.io.File old = new java.io.File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
            if (old.exists()) old.delete();
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(apk))
                .setTitle("ELBAS AI " + (version == null ? "" : version)).setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, name);
            downloadId = dm.enqueue(r);
            BroadcastReceiver done = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id != downloadId) return;
                    try { app.unregisterReceiver(this); } catch (Exception ignored) { }
                    install(app, dm, id, apk);
                }
            };
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(done, f, Context.RECEIVER_EXPORTED); else app.registerReceiver(done, f);
            Toast.makeText(a, a.getString(R.string.upd_downloading), Toast.LENGTH_LONG).show();
        } catch (Exception e) { browser(a, apk); }
    }

    private static void install(Context app, DownloadManager dm, long id, String apk) {
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c == null || !c.moveToFirst() || c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) { browser(app, apk); return; }
            Uri u = dm.getUriForDownloadedFile(id);
            if (u == null) { browser(app, apk); return; }
            app.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { browser(app, apk); }
    }

    private static void browser(Context c, String apk) {
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apk)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }
}
