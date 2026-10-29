package com.example.safesphere.history;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.AlertAttempt;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

public class HistoryDetailsActivity extends AppCompatActivity {

    private static final String EXTRA_EVENT_ID = "event_id";

    private long eventId;
    private int userId;
    @Nullable
    private SafetyEvent event;

    public static Intent intent(Context context, long eventId) {
        return new Intent(context, HistoryDetailsActivity.class).putExtra(EXTRA_EVENT_ID, eventId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_details);
        UiUtils.setupToolbar(this, R.string.history_details_title);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, -1);
        userId = new SessionManager(this).getUserId();
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnOpenMap).setOnClickListener(v -> {
            if (event != null && event.hasLocation) UiUtils.openMap(this, event.latitude, event.longitude);
        });
        load();
    }

    private void load() {
        Context app = getApplicationContext();
        AppExecutors.io(() -> {
            EventRepository repo = new EventRepository(app);
            SafetyEvent e = repo.getEvent(eventId);
            List<AlertAttempt> attempts = e != null ? repo.getAttempts(eventId) : null;
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (e == null || e.userId != userId) {
                    finish();
                    return;
                }
                bind(e, attempts);
            });
        });
    }

    private void bind(SafetyEvent e, List<AlertAttempt> attempts) {
        event = e;
        ((TextView) findViewById(R.id.tvTitle)).setText(StatusUi.eventTitle(this, e));
        StatusUi.applyEventStatus(findViewById(R.id.tvChip), e);

        LinearLayout rows = findViewById(R.id.detailRows);
        rows.removeAllViews();
        addRow(rows, R.string.history_row_started, TimeUtils.formatDateTime(this, e.startedAt), null);
        if (e.endedAt > 0) addRow(rows, R.string.history_row_ended, TimeUtils.formatDateTime(this, e.endedAt), null);

        boolean sos = SafetyEvent.TYPE_SOS.equals(e.type);
        if (sos) {
            addRow(rows, R.string.sos_trigger_type, StatusUi.triggerLabel(this, e.trigger), null);
            addRow(rows, R.string.sos_alert_status, getString(StatusUi.labelFor(e.alertStatus)), e.alertStatus);
            addRow(rows, R.string.sos_evidence_status, getString(StatusUi.labelFor(e.evidenceStatus)), e.evidenceStatus);
        }
        if (SafetyEvent.TYPE_JOURNEY.equals(e.type)) {
            addRow(rows, R.string.journey_destination, e.destination, null);
            addRow(rows, R.string.journey_eta, TimeUtils.formatDateTime(this, e.expectedArrival), null);
            if (e.notes != null) addRow(rows, R.string.journey_route_optional, e.notes, null);
        } else if (e.notes != null) {
            addRow(rows, R.string.history_row_notes, e.notes, null);
        }
        if (sos || e.hasLocation) {
            String loc = e.hasLocation
                    ? String.format(Locale.US, "%.5f, %.5f", e.latitude, e.longitude) + " · "
                    + getString(R.string.location_accuracy_value, Math.round(e.accuracyMeters))
                    : getString(R.string.emergency_location_failed);
            addRow(rows, R.string.sos_location_status, loc, e.locationStatus);
        }
        findViewById(R.id.btnOpenMap).setVisibility(e.hasLocation ? View.VISIBLE : View.GONE);

        boolean hasAttempts = attempts != null && !attempts.isEmpty();
        findViewById(R.id.tvAlertsHeader).setVisibility(hasAttempts ? View.VISIBLE : View.GONE);
        findViewById(R.id.alertsCard).setVisibility(hasAttempts ? View.VISIBLE : View.GONE);
        LinearLayout list = findViewById(R.id.attemptList);
        list.removeAllViews();
        if (hasAttempts) {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (AlertAttempt a : attempts) {
                View item = inflater.inflate(R.layout.item_alert_attempt, list, false);
                ((TextView) item.findViewById(R.id.tvName)).setText(a.guardianName);
                ((TextView) item.findViewById(R.id.tvDetail)).setText(a.phoneNumber + (a.error != null ? " · " + a.error : ""));
                StatusUi.apply(item.findViewById(R.id.tvChip), a.status);
                list.addView(item);
            }
        }
        // Open records are managed from their own screens.
        findViewById(R.id.btnDelete).setEnabled(!e.isOpen());
    }

    private void addRow(LinearLayout parent, int label, String value, @Nullable String status) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_detail_row, parent, false);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvValue)).setText(value);
        TextView chip = row.findViewById(R.id.tvChip);
        if (status != null) {
            chip.setVisibility(View.VISIBLE);
            StatusUi.apply(chip, status);
        }
        parent.addView(row);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_delete_title)
                .setMessage(R.string.history_delete_message)
                .setPositiveButton(R.string.common_delete, (d, w) -> {
                    Context app = getApplicationContext();
                    AppExecutors.io(() -> {
                        boolean ok = new EventRepository(app).delete(userId, eventId);
                        AppExecutors.main(() -> {
                            Toast.makeText(app, ok ? R.string.history_deleted : R.string.history_delete_failed,
                                    Toast.LENGTH_SHORT).show();
                            if (ok && !isDestroyed()) finish();
                        });
                    });
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }
}
