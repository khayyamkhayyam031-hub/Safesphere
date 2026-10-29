package com.example.safesphere.home;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.safesphere.R;
import com.example.safesphere.auth.WelcomeActivity;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.guardians.GuardiansFragment;
import com.example.safesphere.history.HistoryFragment;
import com.example.safesphere.journey.JourneyFragment;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.settings.SafetyPreferences;
import com.example.safesphere.settings.SettingsFragment;
import com.example.safesphere.sos.SosCountdownActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Bottom-navigation shell. Tabs are kept alive with show/hide so their state survives tab
 * switches. Shake SOS is detected here, so it only works while SafeHer is open on screen.
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static final String EXTRA_TAB = "tab";
    private static final String STATE_TAB = "selected_tab";

    private static final float SHAKE_THRESHOLD_G = 2.6f;
    private static final int SHAKES_REQUIRED = 3;
    private static final long SHAKE_WINDOW_MS = 1500L;
    private static final long SHAKE_COOLDOWN_MS = 5000L;

    private BottomNavigationView bottomNav;
    private SensorManager sensorManager;
    private long firstShakeAt;
    private long lastShakeAt;
    private int shakeCount;
    private long lastTriggerAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new SessionManager(this).isLoggedIn()) {
            startActivity(new Intent(this, WelcomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> { });

        int tab = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_TAB, R.id.nav_home)
                : getIntent().getIntExtra(EXTRA_TAB, R.id.nav_home);
        bottomNav.setSelectedItemId(tab);
        showTab(tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        if (tab != 0) selectTab(tab);
    }

    public void selectTab(@IdRes int tabId) {
        bottomNav.setSelectedItemId(tabId);
    }

    private void showTab(@IdRes int tabId) {
        FragmentManager fm = getSupportFragmentManager();
        String tag = "tab_" + tabId;
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (Fragment f : fm.getFragments()) {
            if (!tag.equals(f.getTag()) && !f.isHidden()) tx.hide(f);
        }
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            tx.add(R.id.fragmentContainer, createTab(tabId), tag);
        } else {
            tx.show(target);
        }
        tx.commitNow();
    }

    private Fragment createTab(@IdRes int tabId) {
        if (tabId == R.id.nav_journey) return new JourneyFragment();
        if (tabId == R.id.nav_guardians) return new GuardiansFragment();
        if (tabId == R.id.nav_history) return new HistoryFragment();
        if (tabId == R.id.nav_settings) return new SettingsFragment();
        return new HomeFragment();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNav != null) outState.putInt(STATE_TAB, bottomNav.getSelectedItemId());
    }

    // ---------- Shake SOS (foreground only) ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && new SafetyPreferences(this).isShakeSosEnabled()) {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = event.values[2] / SensorManager.GRAVITY_EARTH;
        double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (g < SHAKE_THRESHOLD_G) return;

        long now = System.currentTimeMillis();
        if (now - lastShakeAt < 200) return; // same jolt
        if (now - firstShakeAt > SHAKE_WINDOW_MS) {
            firstShakeAt = now;
            shakeCount = 0;
        }
        lastShakeAt = now;
        shakeCount++;
        if (shakeCount >= SHAKES_REQUIRED && now - lastTriggerAt > SHAKE_COOLDOWN_MS) {
            lastTriggerAt = now;
            shakeCount = 0;
            startSos(SafetyEvent.TRIGGER_SHAKE);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /** Every SOS entry point goes through the cancellable countdown. */
    public void startSos(String trigger) {
        startActivity(SosCountdownActivity.intent(this, trigger));
    }

    @Nullable
    public static MainActivity from(@Nullable Fragment f) {
        return f != null && f.getActivity() instanceof MainActivity ? (MainActivity) f.getActivity() : null;
    }
}
