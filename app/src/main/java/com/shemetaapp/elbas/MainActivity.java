package com.shemetaapp.elbas;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.CancellationSignal;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import org.json.JSONObject;

/** ELBAS AI in an app: the website in a WebView, plus phone permissions (microphone, notifications, full-screen calls). */
public class MainActivity extends Activity {
    private static final int REQ_PERMS = 7, REQ_FILE = 8;
    private WebView web;
    private PermissionRequest pendingMic;
    private ValueCallback<Uri[]> fileCb;
    static volatile boolean visible = false;
    static volatile String currentUrl = null;
    private long backAt = 0;
    private static java.lang.ref.WeakReference<MainActivity> current = new java.lang.ref.WeakReference<>(null);
    private boolean bgAsked = false, bgFixing = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " ElbasApp/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        restoreSignIn();
        web.addJavascriptInterface(new Bridge(), "ElbasApp");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("ai.shemetaapp.com".equals(u.getHost()) && "https".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
                return true;   // other sites (telebirr, Telegram, job sites…) open outside the app
            }
            @Override public void onPageFinished(WebView v, String url) { keepSignIn(url); currentUrl = url; rememberPage(url); clearChat(url); }
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) { currentUrl = url; clearChat(url); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    if (!"ai.shemetaapp.com".equals(req.getOrigin().getHost())) { req.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    else { pendingMic = req; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMS); }
                });
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                fileCb = cb;
                try { startActivityForResult(p.createIntent(), REQ_FILE); } catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        // Downloads (the app update APK, invoices…) go to the phone's browser, which saves them.
        web.setDownloadListener((url, ua, cd, mime, len) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { } });
        askPermissions();
        open(getIntent());
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
        else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
    }

    private void open(Intent i) {
        if (i.getBooleanExtra("update", false)) { i.removeExtra("update"); web.postDelayed(() -> checkUpdate(true), 800); }
        String ans = i.getStringExtra("answer");
        if (ans != null) CallNotifier.stop(this, ans);
        Uri d = i.getData();
        String url = d != null && "ai.shemetaapp.com".equals(d.getHost()) ? d.toString() : lastPage();
        // Google sign-in finished in the phone's browser: it hands back a one-time code (elbas://login?code=…)
        if (d != null && "elbas".equals(d.getScheme()) && "login".equals(d.getHost())) {
            String code = d.getQueryParameter("code");
            if (code != null && code.matches("[0-9a-f]{32}")) url = BuildConfig.SITE + "/auth/app?code=" + code;
        }
        /* 1.8+: Share a telebirr SMS to ELBAS → the "top up with the telebirr SMS" page opens with the text already pasted. */ String shared = Intent.ACTION_SEND.equals(i.getAction()) && "text/plain".equals(i.getType()) ? i.getStringExtra(Intent.EXTRA_TEXT) : null; if (shared != null && !shared.trim().isEmpty()) { url = BuildConfig.SITE + "/wallet/sms"; i.setAction(Intent.ACTION_MAIN); final String st = shared.length() > 2000 ? shared.substring(0, 2000) : shared; web.postDelayed(new Runnable() { int n = 0; @Override public void run() { web.evaluateJavascript("(window.elbasSharedText && window.elbasSharedText(" + org.json.JSONObject.quote(st) + ")) ? 1 : 0", v -> { if (!"1".equals(v) && ++n < 30) web.postDelayed(this, 500); }); } }, 900); }
        web.loadUrl(url);
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); open(i); }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_PERMS && pendingMic != null) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingMic.deny();
            pendingMic = null;
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE && fileCb != null) { fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data)); fileCb = null; }
        else super.onActivityResult(code, result, data);
    }

    /**
     * Back: the page decides first (close a sheet or menu, or go UP: item → its service → Chat home).
     * On the main page, Back twice closes the app instead of walking through old pages.
     */
    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){try{return window.elbasBack?window.elbasBack():'none'}catch(e){return 'none'}})()", r -> {
            if ("\"handled\"".equals(r)) return;
            if ("\"none\"".equals(r) && web.canGoBack()) { web.goBack(); return; }
            long now = System.currentTimeMillis();
            if (now - backAt < 2000) { finish(); return; }
            backAt = now;
            Toast.makeText(this, getString(R.string.back_again), Toast.LENGTH_SHORT).show();
        });
    }

    // ---- Come back to the same page: reopening the app within 30 minutes returns to where you were.
    private void rememberPage(String url) {
        if (url == null || !url.startsWith(BuildConfig.SITE + "/") || url.matches(".*/(login|register|logout|auth|verify|reset|forgot|offline|welcome)([/?#].*)?$")) return;
        if (url.contains("go=1")) return;   // never re-open a call
        getSharedPreferences("elbas", MODE_PRIVATE).edit().putString("last", url).putLong("last_at", System.currentTimeMillis()).apply();
    }

    private String lastPage() {
        android.content.SharedPreferences p = getSharedPreferences("elbas", MODE_PRIVATE);
        String u = p.getString("last", "");
        if (u.startsWith(BuildConfig.SITE + "/") && System.currentTimeMillis() - p.getLong("last_at", 0) < 30 * 60 * 1000L) return u;
        return BuildConfig.SITE + "/chat";
    }

    // ---- New app version: checked every time the app comes to the front (network at most every 10 min);
    // while this app is older than the one on ELBAS, it asks again each time it is opened (at most every 30 min).
    // "Update now" downloads the new APK inside the app and opens Android's installer.
    private volatile boolean updDialogOpen = false;

    private void checkUpdate(boolean force) {
        android.content.SharedPreferences p = getSharedPreferences("elbas", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String cachedLatest = p.getString("upd_latest", ""), cachedApk = p.getString("upd_apk", "");
        if (!force && now - p.getLong("upd_checked", 0) < 10 * 60 * 1000L) { askUpdate(cachedLatest, cachedApk, p.getString("upd_title", ""), p.getString("upd_text", ""), p.getString("upd_yes", ""), p.getString("upd_later", ""), false); return; }
        p.edit().putLong("upd_checked", now).apply();
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(BuildConfig.SITE + "/api/app/config").openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "ElbasApp/" + BuildConfig.VERSION_NAME);
                String cookie = CookieManager.getInstance().getCookie(BuildConfig.SITE); if (cookie != null) c.setRequestProperty("Cookie", cookie);
                if (c.getResponseCode() != 200) return;
                java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) > 0 && bo.size() < 65536) bo.write(buf, 0, n);
                JSONObject j = new JSONObject(bo.toString("UTF-8"));
                String latest = j.optString("latest", ""), apk = j.optString("apk", "");
                String title = j.optString("updateTitle", "Update available"), text = j.optString("updateText", "A new version of ELBAS AI is ready."),
                    yes = j.optString("updateBtn", "Update now"), later = j.optString("laterBtn", "Later");
                p.edit().putString("upd_latest", latest).putString("upd_apk", apk).putString("upd_title", title).putString("upd_text", text)
                    .putString("upd_yes", yes).putString("upd_later", later).apply();
                askUpdate(latest, apk, title, text, yes, later, force);
            } catch (Exception ignored) { }
        }).start();
    }

    private void askUpdate(String latest, String apk, String title, String text, String yes, String later, boolean force) {
        if (!newer(latest, BuildConfig.VERSION_NAME) || !apk.startsWith(BuildConfig.SITE + "/")) return;
        android.content.SharedPreferences p = getSharedPreferences("elbas", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && now - p.getLong("upd_asked", 0) < 30 * 60 * 1000L) return;
        runOnUiThread(() -> {
            if (isFinishing() || !visible || updDialogOpen) return;
            p.edit().putLong("upd_asked", System.currentTimeMillis()).apply();
            updDialogOpen = true;
            new AlertDialog.Builder(this).setTitle(title.isEmpty() ? "Update available" : title)
                .setMessage(text.isEmpty() ? "A new version of ELBAS AI is ready." : text).setCancelable(true)
                .setPositiveButton(yes.isEmpty() ? "Update now" : yes, (dlg, w) -> Updater.start(this, apk, latest))
                .setNegativeButton(later.isEmpty() ? "Later" : later, null)
                .setOnDismissListener(d -> updDialogOpen = false).show();
        });
    }

    /** "1.10" is newer than "1.9". */
    static boolean newer(String a, String b) {
        if (a == null || !a.matches("\\d+(\\.\\d+){0,3}")) return false;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i].replaceAll("\\D.*", "").isEmpty() ? "0" : y[i].replaceAll("\\D.*", "")) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    @Override protected void onResume() {
        super.onResume(); visible = true; current = new java.lang.ref.WeakReference<>(this); Push.start(this); checkUpdate(false); Updater.resume(this);
        clearChat(currentUrl);
        if (bgFixing) { bgFixing = false; web.postDelayed(() -> checkBackground(true), 600); }   // back from a settings screen: next step
        else web.postDelayed(() -> checkBackground(false), 6000);
    }
    @Override protected void onPause() { super.onPause(); visible = false; CookieManager.getInstance().flush(); }

    /** A new version was announced while the app is on screen: ask now. */
    static void updateNow() {
        MainActivity a = current.get();
        if (a == null) return;
        a.runOnUiThread(() -> a.checkUpdate(true));
    }

    /** A message arrived while the app is on screen: let the page show its banner and play its tone now. */
    static void alertsCheck() {
        MainActivity a = current.get();
        if (a == null || a.web == null) return;
        a.runOnUiThread(() -> { try { a.web.evaluateJavascript("window.elbasAlertsCheck&&window.elbasAlertsCheck()", null); } catch (Exception ignored) { } });
    }

    private void clearChat(String url) {
        if (url == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/messages\\?(?:.*&)?t=([a-f0-9]{24})").matcher(url);
        if (m.find()) MsgNotifier.clear(this, m.group(1));
    }

    // ---- Messages and calls while ELBAS is closed need: notifications on, no battery limit, full-screen calls (Android 14+).
    private boolean notifOk() { return getSystemService(NotificationManager.class).areNotificationsEnabled(); }
    private boolean batteryOk() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
    }
    private boolean fullOk() { return Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager.class).canUseFullScreenIntent(); }
    boolean backgroundOk() { return notifOk() && batteryOk() && fullOk(); }

    private void checkBackground(boolean continuing) {
        if (!visible || isFinishing() || backgroundOk()) return;
        android.content.SharedPreferences p = getSharedPreferences("elbas", MODE_PRIVATE);
        if (!continuing) {
            if (bgAsked || System.currentTimeMillis() - p.getLong("bg_asked", 0) < 3 * 86400000L) return;   // ask at most every 3 days
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !p.getBoolean("notif_asked", false)) { p.edit().putBoolean("notif_asked", true).apply(); return; }   // the system question comes first
            bgAsked = true; p.edit().putLong("bg_asked", System.currentTimeMillis()).apply();
            new AlertDialog.Builder(this).setTitle(R.string.bg_title).setMessage(R.string.bg_text).setCancelable(true)
                .setPositiveButton(R.string.bg_allow, (d, w) -> fixBackground()).setNegativeButton(R.string.bg_later, null).show();
        } else fixBackground();
    }

    /** One step at a time: notifications → battery → full-screen calls. Each opens the right phone setting. */
    void fixBackground() {
        try {
            if (!notifOk()) {
                bgFixing = true;
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } else if (!batteryOk()) {
                bgFixing = true;
                try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            } else if (!fullOk()) {
                bgFixing = true;
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
            } else Toast.makeText(this, getString(R.string.bg_done), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            bgFixing = false;
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
        }
    }
    @Override protected void onStop() { super.onStop(); CookieManager.getInstance().flush(); }

    // ---- Stay signed in: write cookies to disk right away (Android only saves them every ~30 s, so a
    // quickly closed app lost the sign-in) and keep a private copy of the site's "stay signed in" cookie.
    private static final String KEEP = "elbas_keep";
    private boolean restoredOnce = false;

    private void restoreSignIn() {
        CookieManager cm = CookieManager.getInstance();
        String now = cm.getCookie(BuildConfig.SITE);
        String saved = getSharedPreferences("elbas", MODE_PRIVATE).getString(KEEP, "");
        if ((now == null || !now.contains(KEEP + "=")) && saved.matches("[0-9a-f]{24}:[0-9a-f]{64}")) {
            cm.setCookie(BuildConfig.SITE, KEEP + "=" + saved + "; Max-Age=34560000; Path=/; Secure; HttpOnly; SameSite=Lax");
            cm.flush();
            restoredOnce = true;
        }
    }

    private void keepSignIn(String url) {
        if (url == null || !url.startsWith(BuildConfig.SITE)) return;
        CookieManager cm = CookieManager.getInstance();
        cm.flush();
        String all = cm.getCookie(BuildConfig.SITE);
        String val = null;
        if (all != null) for (String part : all.split(";")) { String t = part.trim(); if (t.startsWith(KEEP + "=")) val = t.substring(KEEP.length() + 1); }
        android.content.SharedPreferences.Editor e = getSharedPreferences("elbas", MODE_PRIVATE).edit();
        if (val != null && val.matches("[0-9a-f]{24}:[0-9a-f]{64}")) e.putString(KEEP, val).apply();
        else if (val == null) e.remove(KEEP).apply();   // signed out (or the server refused the old cookie)
        restoredOnce = false;
    }

    // ---- Google sign-in inside the app (Google's own account sheet) — never opens a browser.
    private void startGoogle(String clientId) {
        if (clientId == null || !clientId.matches("[0-9]+-[A-Za-z0-9_]+\\.apps\\.googleusercontent\\.com")) { googleDone(null, "config"); return; }
        try {
            GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(clientId).build();
            GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
            CredentialManager.create(this).getCredentialAsync(this, req, new CancellationSignal(), this::runOnUiThread,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse r) {
                        Credential c = r.getCredential();
                        if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try { googleDone(GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData()).getIdToken(), null); }
                            catch (Exception e) { googleDone(null, "parse"); }
                        } else googleDone(null, "type");
                    }
                    @Override public void onError(GetCredentialException e) { googleDone(null, e.getClass().getSimpleName()); }
                });
        } catch (Throwable t) { googleDone(null, "unavailable"); }
    }

    private void googleDone(String token, String err) {
        String js = "window.elbasGoogle&&window.elbasGoogle(" + (token == null ? "null" : JSONObject.quote(token)) + "," + (err == null ? "null" : JSONObject.quote(err)) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    /** window.ElbasApp in the web page. */
    final class Bridge {
        @JavascriptInterface public void googleSignIn(String clientId) { runOnUiThread(() -> startGoogle(clientId)); }
        @JavascriptInterface public String token() { return Push.token(MainActivity.this); }
        @JavascriptInterface public String version() { return BuildConfig.VERSION_NAME; }
        /** 1.4+: the app shows its own update popup, so the web page does not need to. */
        @JavascriptInterface public boolean hasUpdater() { return true; }
        /** 1.6+: the app asks for every new version each time it is opened, so the page does not need to. */
        @JavascriptInterface public boolean autoUpdate() { return true; } /* 1.7+: Share with a friend opens the Android share sheet. */ @JavascriptInterface public void share(String text) { final String t = text == null ? "" : text.length() > 600 ? text.substring(0, 600) : text; runOnUiThread(() -> { try { Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t).putExtra(Intent.EXTRA_SUBJECT, "ELBAS AI"); startActivity(Intent.createChooser(send, "ELBAS AI")); } catch (Exception ignored) { } }); }
        /** 1.5+: are notifications, battery and full-screen calls all set for ELBAS to alert while closed? */
        @JavascriptInterface public boolean backgroundOk() { return MainActivity.this.backgroundOk(); }
        /** 1.5+: open the next phone setting that is still missing. */
        @JavascriptInterface public void fixBackground() { runOnUiThread(MainActivity.this::fixBackground); }
        /** 1.5+: the page shows message banners itself while the app is open. */
        @JavascriptInterface public boolean hasMsgAlerts() { return true; }
        /** Android 14+: can this app show the full-screen call screen? */
        @JavascriptInterface public boolean canFullScreen() {
            return Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager.class).canUseFullScreenIntent();
        }
        @JavascriptInterface public void openCallSettings() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 34) startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                } catch (Exception ignored) { }
            });
        }
    }
}
