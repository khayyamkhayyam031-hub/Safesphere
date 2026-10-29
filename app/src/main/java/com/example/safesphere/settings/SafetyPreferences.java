package com.example.safesphere.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-level safety settings. Only features that are actually implemented have a switch. */
public class SafetyPreferences {

    private static final String PREF_NAME = "safeher_settings";
    private static final String KEY_COUNTDOWN_SECONDS = "sos_countdown_seconds";
    private static final String KEY_SHAKE_SOS = "shake_sos_enabled";
    private static final String KEY_BIOMETRIC_LOCK = "biometric_lock_enabled";
    private static final String KEY_FAKE_CALLER = "fake_call_caller_name";
    private static final String KEY_FAKE_DELAY = "fake_call_delay_seconds";

    public static final int[] COUNTDOWN_OPTIONS = {3, 5, 10};
    public static final int[] FAKE_CALL_DELAYS = {0, 10, 30, 60};

    private final SharedPreferences prefs;

    public SafetyPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getCountdownSeconds() {
        return prefs.getInt(KEY_COUNTDOWN_SECONDS, 5);
    }

    public void setCountdownSeconds(int seconds) {
        prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, seconds).apply();
    }

    public boolean isShakeSosEnabled() {
        return prefs.getBoolean(KEY_SHAKE_SOS, false);
    }

    public void setShakeSosEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHAKE_SOS, enabled).apply();
    }

    public boolean isBiometricLockEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_LOCK, false);
    }

    public void setBiometricLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply();
    }

    public String getFakeCallerName(String fallback) {
        return prefs.getString(KEY_FAKE_CALLER, fallback);
    }

    public void setFakeCallerName(String name) {
        prefs.edit().putString(KEY_FAKE_CALLER, name).apply();
    }

    public int getFakeCallDelaySeconds() {
        return prefs.getInt(KEY_FAKE_DELAY, 10);
    }

    public void setFakeCallDelaySeconds(int seconds) {
        prefs.edit().putInt(KEY_FAKE_DELAY, seconds).apply();
    }

    /** Called when the local account is deleted. */
    public void clear() {
        prefs.edit().clear().apply();
    }
}
