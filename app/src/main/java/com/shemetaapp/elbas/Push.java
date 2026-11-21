package com.shemetaapp.elbas;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Firebase is configured at run time from https://ai.shemetaapp.com/api/app/config (public Firebase app settings),
 * so the same APK works before and after the owner sets up Firebase. The device token is handed to the web page,
 * which registers it for the signed-in person (POST /api/push/native).
 */
public final class Push {
    private static final String PREFS = "elbas";
    private Push() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static String token(Context c) { return prefs(c).getString("fcm_token", ""); }

    public static void start(Context c) {
        final Context app = c.getApplicationContext();
        String cached = prefs(app).getString("fcm_config", "");
        if (!cached.isEmpty()) init(app, cached);
        new Thread(() -> {
            try {
                String cfg = http("GET", BuildConfig.SITE + "/api/app/config", null);
                JSONObject j = new JSONObject(cfg);
                if (j.optString("projectId").isEmpty()) return;
                if (!cfg.equals(cached)) { prefs(app).edit().putString("fcm_config", cfg).apply(); init(app, cfg); }
            } catch (Exception ignored) { }
        }).start();
    }

    private static synchronized void init(Context app, String cfg) {
        try {
            JSONObject j = new JSONObject(cfg);
            if (FirebaseApp.getApps(app).isEmpty()) {
                FirebaseOptions o = new FirebaseOptions.Builder()
                    .setApiKey(j.getString("apiKey")).setApplicationId(j.getString("appId"))
                    .setProjectId(j.getString("projectId")).setGcmSenderId(j.getString("senderId")).build();
                FirebaseApp.initializeApp(app, o);
            }
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> saveToken(app, t));
        } catch (Exception ignored) { }
    }

    static void saveToken(Context c, String t) {
        if (t != null && !t.isEmpty()) prefs(c).edit().putString("fcm_token", t).apply();
    }

    /** Tell ELBAS this phone declined the call (the device token proves who it is). */
    static void decline(Context c, String callToken) {
        final String dev = token(c);
        new Thread(() -> {
            try {
                http("POST", BuildConfig.SITE + "/api/calls/native-decline",
                    "t=" + URLEncoder.encode(callToken, "UTF-8") + "&device=" + URLEncoder.encode(dev, "UTF-8"));
            } catch (Exception ignored) { }
        }).start();
    }

    static String http(String method, String url, String form) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method); h.setConnectTimeout(10000); h.setReadTimeout(15000);
        h.setRequestProperty("Accept", "application/json");
        h.setRequestProperty("X-Elbas-App", "android");
        if (form != null) {
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream o = h.getOutputStream()) { o.write(form.getBytes(StandardCharsets.UTF_8)); }
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getResponseCode() < 400 ? h.getInputStream() : h.getErrorStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
