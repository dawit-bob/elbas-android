package com.shemetaapp.elbas;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Data messages from ELBAS: "call" rings this phone (full-screen call screen), "cancel" stops the ringing, "msg" is a new chat message. */
public class ElbasMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) { Push.saveToken(this, token); }

    @Override public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        String type = d.get("type");
        if ("msg".equals(type)) { MsgNotifier.show(this, d.get("thread"), d.get("title"), d.get("from"), d.get("text"), d.get("id"), d.get("group")); return; }
        String tok = d.get("token");
        if (tok == null || !tok.matches("[a-f0-9]{32}")) return;
        if ("call".equals(type)) CallNotifier.ring(this, tok, d.get("caller"), d.get("business"));
        else if ("cancel".equals(type)) CallNotifier.stop(this, tok);
    }
}
