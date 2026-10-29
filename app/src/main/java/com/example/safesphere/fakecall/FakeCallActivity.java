package com.example.safesphere.fakecall;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.settings.SafetyPreferences;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

/**
 * A simulated incoming call to help the user leave an uncomfortable situation. No real call is
 * placed or received; the ringtone and screen are local. The timer only runs while SafeHer is
 * alive, and Android will not bring this screen to the front from the background.
 */
public class FakeCallActivity extends AppCompatActivity {

    private enum State { SETUP, SCHEDULED, RINGING, IN_CALL }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private State state = State.SETUP;
    private long fireAt;
    private long callStartedAt;
    private Ringtone ringtone;
    private ObjectAnimator pulse;
    private String callerName;

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            long left = fireAt - System.currentTimeMillis();
            if (left <= 0) {
                startRinging();
                return;
            }
            ((TextView) findViewById(R.id.tvScheduled)).setText(getString(R.string.fake_call_in, (left + 999) / 1000));
            handler.postDelayed(this, 250);
        }
    };

    private final Runnable callTimer = new Runnable() {
        @Override
        public void run() {
            ((TextView) findViewById(R.id.tvCallState)).setText(TimeUtils.formatDuration(System.currentTimeMillis() - callStartedAt));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call);
        UiUtils.setupToolbar(this, R.string.fake_call_title);

        SafetyPreferences prefs = new SafetyPreferences(this);
        TextInputLayout tilCaller = findViewById(R.id.tilCaller);
        tilCaller.getEditText().setText(prefs.getFakeCallerName(getString(R.string.fake_call_default_caller)));

        ChipGroup chips = findViewById(R.id.delayChips);
        int saved = prefs.getFakeCallDelaySeconds();
        for (int seconds : SafetyPreferences.FAKE_CALL_DELAYS) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(seconds);
            chip.setCheckable(true);
            chip.setText(seconds == 0 ? getString(R.string.fake_call_now)
                    : seconds < 60 ? getString(R.string.fake_call_seconds, seconds)
                    : getString(R.string.fake_call_minutes, seconds / 60));
            chips.addView(chip);
            if (seconds == saved) chip.setChecked(true);
        }

        findViewById(R.id.btnSchedule).setOnClickListener(v -> schedule());
        findViewById(R.id.btnCancelSchedule).setOnClickListener(v -> cancelSchedule());
        findViewById(R.id.btnAccept).setOnClickListener(v -> accept());
        findViewById(R.id.btnDecline).setOnClickListener(v -> endCall());
        findViewById(R.id.btnEndCall).setOnClickListener(v -> endCall());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (state == State.RINGING || state == State.IN_CALL) endCall();
                else {
                    cancelSchedule();
                    finish();
                }
            }
        });
    }

    private void schedule() {
        TextInputLayout tilCaller = findViewById(R.id.tilCaller);
        String name = tilCaller.getEditText().getText().toString().trim();
        if (name.isEmpty()) name = getString(R.string.fake_call_default_caller);
        callerName = name;

        ChipGroup chips = findViewById(R.id.delayChips);
        Chip checked = findViewById(chips.getCheckedChipId());
        int delay = checked != null ? (int) checked.getTag() : 10;

        SafetyPreferences prefs = new SafetyPreferences(this);
        prefs.setFakeCallerName(name);
        prefs.setFakeCallDelaySeconds(delay);

        if (delay == 0) {
            startRinging();
            return;
        }
        state = State.SCHEDULED;
        fireAt = System.currentTimeMillis() + delay * 1000L;
        findViewById(R.id.tvScheduled).setVisibility(View.VISIBLE);
        findViewById(R.id.btnSchedule).setVisibility(View.GONE);
        findViewById(R.id.btnCancelSchedule).setVisibility(View.VISIBLE);
        handler.post(countdown);
    }

    private void cancelSchedule() {
        handler.removeCallbacks(countdown);
        if (state == State.SCHEDULED) state = State.SETUP;
        findViewById(R.id.tvScheduled).setVisibility(View.GONE);
        findViewById(R.id.btnSchedule).setVisibility(View.VISIBLE);
        findViewById(R.id.btnCancelSchedule).setVisibility(View.GONE);
    }

    private void startRinging() {
        handler.removeCallbacks(countdown);
        state = State.RINGING;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        findViewById(R.id.setupGroup).setVisibility(View.GONE);
        findViewById(R.id.callGroup).setVisibility(View.VISIBLE);
        getWindow().setStatusBarColor(getColor(R.color.call_background));
        getWindow().setNavigationBarColor(getColor(R.color.call_background));
        ((TextView) findViewById(R.id.tvCallerName)).setText(callerName);
        ((TextView) findViewById(R.id.tvCallerInitial)).setText(callerName.substring(0, 1).toUpperCase());
        ((TextView) findViewById(R.id.tvCallState)).setText(R.string.fake_call_incoming);
        findViewById(R.id.ringingActions).setVisibility(View.VISIBLE);
        findViewById(R.id.btnEndCall).setVisibility(View.GONE);

        View avatar = findViewById(R.id.callAvatar);
        pulse = ObjectAnimator.ofPropertyValuesHolder(avatar,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        pulse.setDuration(700);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();

        playRingtone();
        vibrate(true);
    }

    private void accept() {
        stopAlerting();
        state = State.IN_CALL;
        callStartedAt = System.currentTimeMillis();
        findViewById(R.id.ringingActions).setVisibility(View.GONE);
        findViewById(R.id.btnEndCall).setVisibility(View.VISIBLE);
        handler.post(callTimer);
    }

    private void endCall() {
        boolean answered = state == State.IN_CALL;
        stopAlerting();
        handler.removeCallbacks(callTimer);
        logEvent(answered);
        state = State.SETUP;
        finish();
    }

    private void logEvent(boolean answered) {
        int userId = new SessionManager(this).getUserId();
        Context app = getApplicationContext();
        long started = answered ? callStartedAt : System.currentTimeMillis();
        AppExecutors.io(() -> {
            SafetyEvent e = new SafetyEvent();
            e.userId = userId;
            e.type = SafetyEvent.TYPE_FAKE_CALL;
            e.status = SafetyEvent.STATUS_COMPLETED;
            e.startedAt = started;
            e.endedAt = System.currentTimeMillis();
            e.locationStatus = SafetyEvent.OP_NOT_STARTED;
            e.alertStatus = SafetyEvent.OP_NOT_STARTED;
            e.evidenceStatus = SafetyEvent.OP_NOT_STARTED;
            e.notes = app.getString(answered ? R.string.fake_call_note_answered : R.string.fake_call_note_declined);
            new EventRepository(app).insert(e);
        });
    }

    private void playRingtone() {
        try {
            Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone == null) return;
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone.setLooping(true);
            ringtone.play();
        } catch (RuntimeException ignored) {
            // No ringtone available; the vibration and screen still work.
        }
    }

    private void vibrate(boolean on) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (!on) {
            v.cancel();
            return;
        }
        long[] pattern = {0, 800, 600};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            v.vibrate(pattern, 0);
        }
    }

    private void stopAlerting() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        vibrate(false);
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopAlerting();
    }
}
