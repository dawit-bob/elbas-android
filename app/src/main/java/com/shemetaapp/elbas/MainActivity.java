package com.shemetaapp.elbas;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
            @Override public void onPageFinished(WebView v, String url) { keepSignIn(url); }
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
        String ans = i.getStringExtra("answer");
        if (ans != null) CallNotifier.stop(this, ans);
        Uri d = i.getData();
        String url = d != null && "ai.shemetaapp.com".equals(d.getHost()) ? d.toString() : BuildConfig.SITE + "/chat";
        // Google sign-in finished in the phone's browser: it hands back a one-time code (elbas://login?code=…)
        if (d != null && "elbas".equals(d.getScheme()) && "login".equals(d.getHost())) {
            String code = d.getQueryParameter("code");
            if (code != null && code.matches("[0-9a-f]{32}")) url = BuildConfig.SITE + "/auth/app?code=" + code;
        }
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

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    @Override protected void onResume() { super.onResume(); Push.start(this); }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
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
