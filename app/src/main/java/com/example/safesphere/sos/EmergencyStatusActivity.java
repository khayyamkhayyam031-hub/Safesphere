package com.example.safesphere.sos;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.AlertAttempt;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.evidence.EvidenceActivity;
import com.example.safesphere.home.MainActivity;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Live view of one SOS event, re-read from SQLite every two seconds while visible so SMS
 * callbacks show up as they arrive. Every row shows the stored outcome, never an assumed one.
 */
public class EmergencyStatusActivity extends AppCompatActivity {

    private static final String EXTRA_EVENT_ID = "event_id";
    private static final long POLL_MS = 2000L;

    private long eventId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            load();
            handler.postDelayed(this, POLL_MS);
        }
    };
    @Nullable
    private SafetyEvent event;

    public static Intent intent(Context context, long eventId) {
        return new Intent(context, EmergencyStatusActivity.class).putExtra(EXTRA_EVENT_ID, eventId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_status);
        UiUtils.setupToolbar(this, R.string.emergency_title);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, -1);

        findViewById(R.id.btnCallPolice).setOnClickListener(v -> UiUtils.dial(this, getString(R.string.helpline_police_number)));
        findViewById(R.id.btnResolve).setOnClickListener(v -> confirmResolve());
        findViewById(R.id.btnRecord).setOnClickListener(v -> startActivity(EvidenceActivity.intent(this, eventId)));
        findViewById(R.id.btnOpenMap).setOnClickListener(v -> {
            if (event != null && event.hasLocation) UiUtils.openMap(this, event.latitude, event.longitude);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        eventId = intent.getLongExtra(EXTRA_EVENT_ID, eventId);
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(poll);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(poll);
    }

    private void load() {
        Context app = getApplicationContext();
        AppExecutors.io(() -> {
            EventRepository repo = new EventRepository(app);
            SafetyEvent e = repo.getEvent(eventId);
            List<AlertAttempt> attempts = repo.getAttempts(eventId);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (e == null) {
                    finish();
                    return;
                }
                bind(e, attempts);
            });
        });
    }

    private void bind(SafetyEvent e, List<AlertAttempt> attempts) {
        event = e;
        boolean open = e.isOpen();

        View header = findViewById(R.id.headerCard);
        header.setBackgroundResource(open ? R.drawable.bg_banner_emergency : R.drawable.bg_banner_info);
        TextView headline = findViewById(R.id.tvHeadline);
        TextView headlineDetail = findViewById(R.id.tvHeadlineDetail);
        int fg = getColor(open ? R.color.white : R.color.primary_dark);
        headline.setTextColor(fg);
        headlineDetail.setTextColor(fg);
        if (open) {
            headline.setText(R.string.emergency_active);
            headlineDetail.setText(getString(R.string.emergency_active_since,
                    TimeUtils.relative(this, e.startedAt)));
        } else {
            headline.setText(R.string.emergency_inactive);
            headlineDetail.setText(getString(R.string.emergency_resolved_at, TimeUtils.formatDateTime(this, e.endedAt)));
        }

        row(R.id.rowEvent, getString(R.string.emergency_row_record), getString(R.string.emergency_record_saved),
                getString(StatusUi.labelFor(SafetyEvent.OP_SAVED_LOCALLY)), StatusUi.Tone.SUCCESS);
        row(R.id.rowStarted, getString(R.string.sos_started_at), TimeUtils.formatDateTime(this, e.startedAt), null, null);
        row(R.id.rowTrigger, getString(R.string.sos_trigger_type), StatusUi.triggerLabel(this, e.trigger), null, null);

        // Location
        String locValue;
        if (e.hasLocation) {
            locValue = String.format(Locale.US, "%.5f, %.5f", e.latitude, e.longitude) + "\n"
                    + getString(R.string.emergency_location_meta, Math.round(e.accuracyMeters),
                    TimeUtils.formatTime(this, e.locationTime));
        } else if (SafetyEvent.OP_IN_PROGRESS.equals(e.locationStatus)) {
            locValue = getString(R.string.emergency_location_searching);
        } else if (SafetyEvent.OP_UNAVAILABLE.equals(e.locationStatus)) {
            locValue = getString(R.string.emergency_location_unavailable);
        } else {
            locValue = getString(R.string.emergency_location_failed);
        }
        row(R.id.rowLocation, getString(R.string.sos_location_status), locValue, e.locationStatus);
        findViewById(R.id.btnOpenMap).setVisibility(e.hasLocation ? View.VISIBLE : View.GONE);

        // Alerts
        String alertStatus = attempts.isEmpty() ? e.alertStatus : EventRepository.summarize(attempts);
        row(R.id.rowAlertSummary, getString(R.string.sos_alert_status), alertSummaryText(alertStatus, attempts), alertStatus);
        bindAttempts(attempts);
        bindAlertHelp(e, alertStatus, attempts);

        // Evidence
        row(R.id.rowEvidence, getString(R.string.sos_evidence_status),
                SafetyEvent.OP_SAVED_LOCALLY.equals(e.evidenceStatus)
                        ? getString(R.string.emergency_evidence_saved)
                        : getString(R.string.emergency_evidence_none),
                e.evidenceStatus);
        findViewById(R.id.btnRecord).setVisibility(open ? View.VISIBLE : View.GONE);

        findViewById(R.id.btnResolve).setVisibility(open ? View.VISIBLE : View.GONE);
    }

    private String alertSummaryText(String status, List<AlertAttempt> attempts) {
        if (attempts.isEmpty()) {
            if (SafetyEvent.OP_PENDING.equals(status)) return getString(R.string.emergency_alert_waiting_location);
            return getString(R.string.emergency_alert_none_sent);
        }
        int ok = 0;
        for (AlertAttempt a : attempts) {
            if (SafetyEvent.OP_SENT.equals(a.status) || SafetyEvent.OP_DELIVERED.equals(a.status)) ok++;
        }
        return getResources().getQuantityString(R.plurals.emergency_alert_count, attempts.size(), ok, attempts.size());
    }

    private void bindAttempts(List<AlertAttempt> attempts) {
        LinearLayout list = findViewById(R.id.attemptList);
        list.removeAllViews();
        list.setVisibility(attempts.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AlertAttempt a : attempts) {
            View item = inflater.inflate(R.layout.item_alert_attempt, list, false);
            ((TextView) item.findViewById(R.id.tvName)).setText(a.guardianName);
            String detail = a.phoneNumber + (a.error != null ? " · " + a.error : "");
            ((TextView) item.findViewById(R.id.tvDetail)).setText(detail);
            StatusUi.apply(item.findViewById(R.id.tvChip), a.status);
            list.addView(item);
        }
    }

    private void bindAlertHelp(SafetyEvent e, String status, List<AlertAttempt> attempts) {
        TextView note = findViewById(R.id.tvAlertNote);
        Button action = findViewById(R.id.btnAlertAction);
        action.setVisibility(View.GONE);
        if (!attempts.isEmpty()) {
            note.setText(R.string.emergency_alert_note_sent_meaning);
            return;
        }
        if (SafetyEvent.OP_UNAVAILABLE.equals(status)) {
            note.setText(R.string.emergency_alert_note_unavailable);
            if (e.isOpen()) {
                action.setVisibility(View.VISIBLE);
                action.setText(R.string.emergency_alert_manual_sms);
                action.setOnClickListener(v -> UiUtils.composeSms(this, "",
                        EmergencyManager.buildManualMessage(this, e)));
            }
        } else {
            note.setText(null);
        }
    }

    private void row(int id, String label, String value, @Nullable String status) {
        row(id, label, value, status == null ? null : getString(StatusUi.labelFor(status)),
                status == null ? null : StatusUi.toneFor(status));
    }

    private void row(int id, String label, String value, @Nullable String chip, @Nullable StatusUi.Tone tone) {
        View row = findViewById(id);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvValue)).setText(value);
        TextView chipView = row.findViewById(R.id.tvChip);
        if (chip == null) {
            chipView.setVisibility(View.GONE);
        } else {
            chipView.setVisibility(View.VISIBLE);
            StatusUi.apply(chipView, chip, tone);
        }
    }

    private void confirmResolve() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.emergency_resolve_title)
                .setMessage(R.string.emergency_resolve_message)
                .setPositiveButton(R.string.emergency_resolve_confirm, (d, w) ->
                        EmergencyManager.resolve(this, eventId, () -> {
                            if (isFinishing() || isDestroyed()) return;
                            startActivity(new Intent(this, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                            finish();
                        }))
                .setNegativeButton(R.string.emergency_resolve_keep, null)
                .show();
    }
}
