package com.example.safesphere.sos;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.settings.SafetyPreferences;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/**
 * Cancellable countdown between an SOS trigger and the emergency workflow. Back counts as
 * cancel. Rotation keeps the remaining time instead of restarting.
 */
public class SosCountdownActivity extends AppCompatActivity {

    private static final String EXTRA_TRIGGER = "trigger";
    private static final String STATE_DEADLINE = "deadline";

    private CountDownTimer timer;
    private long deadline;
    private long totalMs;
    private String trigger;
    private boolean finished;

    public static Intent intent(Context context, String trigger) {
        return new Intent(context, SosCountdownActivity.class).putExtra(EXTRA_TRIGGER, trigger);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_countdown);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        trigger = getIntent().getStringExtra(EXTRA_TRIGGER);
        if (trigger == null) trigger = SafetyEvent.TRIGGER_BUTTON;
        int seconds = new SafetyPreferences(this).getCountdownSeconds();
        totalMs = seconds * 1000L;
        deadline = savedInstanceState != null
                ? savedInstanceState.getLong(STATE_DEADLINE)
                : System.currentTimeMillis() + totalMs;

        ((TextView) findViewById(R.id.tvTrigger)).setText(getString(R.string.sos_triggered_by,
                com.example.safesphere.utils.StatusUi.triggerLabel(this, trigger)));
        ((TextView) findViewById(R.id.tvWhatNext)).setText(R.string.sos_what_next);

        findViewById(R.id.btnCancel).setOnClickListener(v -> cancelSos());
        findViewById(R.id.btnSendNow).setOnClickListener(v -> activate());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelSos();
            }
        });
        if (savedInstanceState == null) vibrate();
        startTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Keeps counting while the screen is off or the app is in the background; only a
        // rotation stops this timer, and the new instance resumes from the same deadline.
        if (timer != null && isChangingConfigurations()) timer.cancel();
    }

    private void startTimer() {
        if (finished) return;
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            activate();
            return;
        }
        TextView tv = findViewById(R.id.tvCountdown);
        CircularProgressIndicator progress = findViewById(R.id.countdownProgress);
        timer = new CountDownTimer(remaining, 50) {
            private long lastSecond = -1;

            @Override
            public void onTick(long millisUntilFinished) {
                long second = (millisUntilFinished + 999) / 1000;
                if (second != lastSecond) {
                    lastSecond = second;
                    tv.setText(String.valueOf(second));
                    tv.setContentDescription(getResources().getQuantityString(R.plurals.sos_seconds_left, (int) second, (int) second));
                }
                progress.setProgressCompat((int) (1000 * millisUntilFinished / totalMs), false);
            }

            @Override
            public void onFinish() {
                progress.setProgressCompat(0, false);
                activate();
            }
        }.start();
    }

    private void cancelSos() {
        if (finished) return;
        finished = true;
        if (timer != null) timer.cancel();
        EmergencyManager.recordCancelled(this, new SessionManager(this).getUserId(), trigger);
        Toast.makeText(this, R.string.sos_cancelled, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void activate() {
        if (finished) return;
        finished = true;
        if (timer != null) timer.cancel();
        int userId = new SessionManager(this).getUserId();
        Context app = getApplicationContext();
        EmergencyManager.activate(this, userId, trigger, eventId -> {
            if (eventId <= 0) {
                Toast.makeText(app, R.string.sos_error_record, Toast.LENGTH_LONG).show();
                if (!isDestroyed()) finish();
                return;
            }
            Intent status = EmergencyStatusActivity.intent(app, eventId);
            status.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(status);
            if (!isDestroyed()) finish();
        });
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 120, 200}, -1));
        } else {
            v.vibrate(new long[]{0, 200, 120, 200}, -1);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DEADLINE, deadline);
    }
}
