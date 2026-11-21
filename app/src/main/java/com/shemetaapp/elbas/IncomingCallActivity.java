package com.shemetaapp.elbas;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen incoming call: shows over the lock screen, rings with the phone's ringtone and vibrates until answered. */
public class IncomingCallActivity extends Activity {
    private MediaPlayer player;
    private Vibrator vib;
    private String token;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver stopper = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (token != null && token.equals(i.getStringExtra("token"))) finishCall(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        token = getIntent().getStringExtra("token");
        String caller = getIntent().getStringExtra("caller"), biz = getIntent().getStringExtra("business");
        setContentView(build(caller == null ? "ELBAS" : caller, biz == null ? "" : biz));
        IntentFilter f = new IntentFilter(CallNotifier.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stopper, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(stopper, f);
        startRinging();
        h.postDelayed(this::finishCall, 60000);   // the caller stops ringing after 60 s
    }

    private View build(String caller, String biz) {
        int dp = (int) getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1E8A47, 0xFF0E2416});
        col.setBackground(bg); col.setPadding(24 * dp, 90 * dp, 24 * dp, 60 * dp);

        TextView kind = text("📞  ELBAS · Incoming call", 15, 0xCCFFFFFF, false); col.addView(kind);
        FrameLayout ring = new FrameLayout(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(150 * dp, 150 * dp); rp.topMargin = 40 * dp; rp.bottomMargin = 28 * dp;
        View wave = new View(this); GradientDrawable wd = new GradientDrawable(); wd.setShape(GradientDrawable.OVAL); wd.setStroke(3 * dp, 0xAA7EE29B); wave.setBackground(wd);
        ring.addView(wave, new FrameLayout.LayoutParams(150 * dp, 150 * dp));
        TextView icon = text("📞", 44, Color.WHITE, false); icon.setGravity(Gravity.CENTER);
        GradientDrawable id = new GradientDrawable(); id.setShape(GradientDrawable.OVAL); id.setColor(0xFF2FA35A); icon.setBackground(id);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(100 * dp, 100 * dp, Gravity.CENTER); ring.addView(icon, ip);
        col.addView(ring, rp);
        ScaleAnimation pulse = new ScaleAnimation(0.8f, 1.25f, 0.8f, 1.25f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        pulse.setDuration(900); pulse.setRepeatMode(Animation.REVERSE); pulse.setRepeatCount(Animation.INFINITE); wave.startAnimation(pulse);

        col.addView(text(caller, 32, Color.WHITE, true));
        if (!biz.isEmpty()) col.addView(text(biz, 17, 0xCCFFFFFF, false));
        View spacer = new View(this); col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER);
        row.addView(button("✕", "Decline", 0xFFD7263D, v -> { Push.decline(this, token); finishCall(); }));
        View gap = new View(this); row.addView(gap, new LinearLayout.LayoutParams(70 * dp, 1));
        row.addView(button("📞", "Answer", 0xFF2FA35A, v -> { done = true; stopRinging(); CallNotifier.stop(this, token); CallNotifier.openCall(this, token); finish(); }));
        col.addView(row);
        return col;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View button(String icon, String label, int color, View.OnClickListener on) {
        int dp = (int) getResources().getDisplayMetrics().density;
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = text(icon, 30, Color.WHITE, false);
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(color); b.setBackground(d);
        b.setOnClickListener(on); b.setContentDescription(label);
        c.addView(b, new LinearLayout.LayoutParams(78 * dp, 78 * dp));
        TextView l = text(label, 15, Color.WHITE, true); l.setPadding(0, 8 * dp, 0, 0); c.addView(l);
        return c;
    }

    private void startRinging() {
        try {
            Uri u = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, u); player.setLooping(true); player.prepare(); player.start();
        } catch (Exception ignored) { }
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] pattern = {0, 800, 500, 800, 500};
        if (vib != null) {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pattern, 0)); else vib.vibrate(pattern, 0);
        }
    }

    private void stopRinging() {
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) { }
        if (vib != null) vib.cancel();
    }

    private boolean done = false;
    private void finishCall() { if (done) return; done = true; stopRinging(); CallNotifier.stop(this, token); finish(); }

    @Override protected void onDestroy() {
        stopRinging(); h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(stopper); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public void onBackPressed() { /* keep the call screen until answered or declined */ }
}
