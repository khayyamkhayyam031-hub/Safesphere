package com.example.safesphere.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Minimal local session: only the signed-in user's id is stored, in private SharedPreferences.
 * No password or hash is ever written here. Callers must still confirm the user row exists
 * (see SplashActivity) rather than trusting this flag alone.
 */
public class SessionManager {

    private static final String PREF_NAME = "safeher_session";
    private static final String KEY_USER_ID = "safeher_user_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void startSession(int userId) {
        prefs.edit().putInt(KEY_USER_ID, userId).apply();
    }

    public void clearSession() {
        prefs.edit().remove(KEY_USER_ID).apply();
    }

    public boolean isLoggedIn() {
        return getUserId() > 0;
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }
}
