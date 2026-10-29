package com.example.safesphere.evidence;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import com.google.android.material.button.MaterialButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.EvidenceItem;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.database.EvidenceRepository;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.EmptyState;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Visible, user-started audio recording saved to private app storage. Recording stops when the
 * screen is left (Android does not allow background microphone use without a foreground
 * service, which this version does not have). Nothing is uploaded: no server is configured.
 */
public class EvidenceActivity extends AppCompatActivity {

    private static final String TAG = "Evidence";
    private static final String EXTRA_EVENT_ID = "event_id";

    private enum State { NOT_STARTED, RECORDING, SAVED_LOCALLY, FAILED }

    private State state = State.NOT_STARTED;
    private MediaRecorder recorder;
    private File currentFile;
    private long recordingStartedAt;
    private long lastDurationMs;
    private long eventId;
    private int userId;
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timer = new Runnable() {
        @Override
        public void run() {
            ((TextView) findViewById(R.id.tvDuration)).setText(
                    TimeUtils.formatDuration(System.currentTimeMillis() - recordingStartedAt));
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<String> micPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startRecording();
                else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.evidence_permission_title)
                            .setMessage(R.string.evidence_permission_denied)
                            .setPositiveButton(R.string.common_open_settings, (d, w) -> UiUtils.openAppSettings(this))
                            .setNegativeButton(R.string.common_not_now, null)
                            .show();
                }
                bind();
            });

    public static Intent intent(Context context, long eventId) {
        return new Intent(context, EvidenceActivity.class).putExtra(EXTRA_EVENT_ID, eventId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evidence);
        UiUtils.setupToolbar(this, R.string.evidence_title);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, 0);
        userId = new SessionManager(this).getUserId();
        findViewById(R.id.btnRecord).setOnClickListener(v -> {
            if (state == State.RECORDING) stopRecording();
            else if (UiUtils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) startRecording();
            else micPermission.launch(Manifest.permission.RECORD_AUDIO);
        });
        bind();
        loadRecordings();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (state == State.RECORDING && !isChangingConfigurations()) {
            stopRecording();
            Toast.makeText(this, R.string.evidence_stopped_on_leave, Toast.LENGTH_LONG).show();
        }
        stopPlayback();
    }

    private void startRecording() {
        File dir = new File(getFilesDir(), "evidence");
        if (!dir.exists() && !dir.mkdirs()) {
            fail();
            return;
        }
        currentFile = new File(dir, "audio_" + System.currentTimeMillis() + ".m4a");
        try {
            recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96_000);
            recorder.setAudioSamplingRate(44_100);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Recorder failed to start", e);
            releaseRecorder();
            fail();
            return;
        }
        state = State.RECORDING;
        recordingStartedAt = System.currentTimeMillis();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.post(timer);
        bind();
    }

    private void stopRecording() {
        handler.removeCallbacks(timer);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        long duration = System.currentTimeMillis() - recordingStartedAt;
        boolean ok = true;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // stop() throws when nothing usable was captured (e.g. stopped immediately).
            ok = false;
        }
        releaseRecorder();
        if (!ok || currentFile == null || !currentFile.exists() || currentFile.length() == 0) {
            if (currentFile != null) //noinspection ResultOfMethodCallIgnored
                currentFile.delete();
            fail();
            return;
        }
        lastDurationMs = duration;
        File file = currentFile;
        currentFile = null;
        Context app = getApplicationContext();
        AppExecutors.io(() -> {
            EvidenceItem item = new EvidenceItem();
            item.userId = userId;
            item.eventId = eventId;
            item.filePath = file.getAbsolutePath();
            item.durationMs = duration;
            item.status = SafetyEvent.OP_SAVED_LOCALLY;
            item.createdAt = System.currentTimeMillis();
            boolean saved = new EvidenceRepository(app).insert(item) > 0;
            if (saved) recordInHistory(app, duration);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (saved) {
                    state = State.SAVED_LOCALLY;
                    bind();
                    loadRecordings();
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    fail();
                }
            });
        });
    }

    private void recordInHistory(Context app, long duration) {
        EventRepository events = new EventRepository(app);
        if (eventId > 0) {
            SafetyEvent sos = events.getEvent(eventId);
            if (sos != null) {
                sos.evidenceStatus = SafetyEvent.OP_SAVED_LOCALLY;
                events.update(sos);
            }
        }
        SafetyEvent e = new SafetyEvent();
        e.userId = userId;
        e.type = SafetyEvent.TYPE_EVIDENCE;
        e.status = SafetyEvent.STATUS_COMPLETED;
        e.startedAt = System.currentTimeMillis() - duration;
        e.endedAt = System.currentTimeMillis();
        e.locationStatus = SafetyEvent.OP_NOT_STARTED;
        e.alertStatus = SafetyEvent.OP_NOT_STARTED;
        e.evidenceStatus = SafetyEvent.OP_SAVED_LOCALLY;
        e.notes = app.getString(R.string.evidence_history_note, TimeUtils.formatDuration(duration));
        events.insert(e);
    }

    private void fail() {
        state = State.FAILED;
        bind();
    }

    private void releaseRecorder() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }

    private void bind() {
        boolean mic = UiUtils.hasPermission(this, Manifest.permission.RECORD_AUDIO);
        TextView chip = findViewById(R.id.tvStateChip);
        TextView detail = findViewById(R.id.tvStateDetail);
        MaterialButton record = findViewById(R.id.btnRecord);
        View circle = findViewById(R.id.micCircle);

        switch (state) {
            case RECORDING:
                StatusUi.apply(chip, getString(R.string.evidence_recording), StatusUi.Tone.DANGER);
                detail.setText(R.string.evidence_recording_detail);
                record.setText(R.string.evidence_stop);
                record.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.emergency_red)));
                record.setIconResource(R.drawable.ic_stop);
                circle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.emergency_red_light)));
                break;
            case SAVED_LOCALLY:
                StatusUi.apply(chip, getString(R.string.status_saved_locally), StatusUi.Tone.SUCCESS);
                ((TextView) findViewById(R.id.tvDuration)).setText(TimeUtils.formatDuration(lastDurationMs));
                detail.setText(R.string.evidence_saved_detail);
                resetRecordButton(record, circle);
                break;
            case FAILED:
                StatusUi.apply(chip, getString(R.string.status_failed), StatusUi.Tone.DANGER);
                detail.setText(R.string.evidence_failed_detail);
                resetRecordButton(record, circle);
                break;
            default:
                StatusUi.apply(chip, getString(R.string.status_not_started), StatusUi.Tone.NEUTRAL);
                detail.setText(mic ? R.string.evidence_ready_detail : R.string.evidence_permission_needed);
                resetRecordButton(record, circle);
                break;
        }

        row(R.id.rowPermission, getString(R.string.evidence_row_permission),
                getString(mic ? R.string.evidence_permission_granted : R.string.evidence_permission_not_granted),
                getString(mic ? R.string.status_chip_ready : R.string.status_chip_off),
                mic ? StatusUi.Tone.SUCCESS : StatusUi.Tone.WARNING);
        row(R.id.rowLocalSave, getString(R.string.evidence_row_local),
                getString(R.string.evidence_local_value),
                getString(StatusUi.labelFor(state == State.SAVED_LOCALLY ? SafetyEvent.OP_SAVED_LOCALLY
                        : state == State.FAILED ? SafetyEvent.OP_FAILED
                        : state == State.RECORDING ? SafetyEvent.OP_IN_PROGRESS : SafetyEvent.OP_NOT_STARTED)),
                state == State.SAVED_LOCALLY ? StatusUi.Tone.SUCCESS
                        : state == State.FAILED ? StatusUi.Tone.DANGER
                        : state == State.RECORDING ? StatusUi.Tone.WARNING : StatusUi.Tone.NEUTRAL);
        row(R.id.rowUpload, getString(R.string.evidence_row_upload), getString(R.string.evidence_upload_none),
                getString(R.string.status_unavailable), StatusUi.Tone.NEUTRAL);
    }

    private void resetRecordButton(MaterialButton record, View circle) {
        record.setText(R.string.evidence_start_recording);
        record.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.primary_deep_purple)));
        record.setIconResource(R.drawable.ic_mic);
        circle.setBackgroundTintList(null);
    }

    private void row(int id, String label, String value, @Nullable String chip, @Nullable StatusUi.Tone tone) {
        View row = findViewById(id);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvValue)).setText(value);
        TextView c = row.findViewById(R.id.tvChip);
        c.setVisibility(chip == null ? View.GONE : View.VISIBLE);
        if (chip != null) StatusUi.apply(c, chip, tone);
    }

    // ---------- Recordings list ----------

    private void loadRecordings() {
        Context app = getApplicationContext();
        AppExecutors.io(() -> {
            List<EvidenceItem> items = new EvidenceRepository(app).getAll(userId);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                LinearLayout list = findViewById(R.id.recordingList);
                list.removeAllViews();
                findViewById(R.id.emptyCard).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (items.isEmpty()) {
                    EmptyState.bind(findViewById(R.id.emptyInclude), R.drawable.ic_mic,
                            R.string.evidence_empty_title, R.string.evidence_empty_message, 0, null);
                }
                LayoutInflater inflater = LayoutInflater.from(this);
                for (EvidenceItem item : items) {
                    View v = inflater.inflate(R.layout.item_recording, list, false);
                    ((TextView) v.findViewById(R.id.tvTitle)).setText(TimeUtils.formatDateTime(this, item.createdAt));
                    ((TextView) v.findViewById(R.id.tvMeta)).setText(getString(R.string.evidence_item_meta,
                            TimeUtils.formatDuration(item.durationMs)));
                    ImageButton play = v.findViewById(R.id.btnPlay);
                    play.setContentDescription(getString(R.string.evidence_play_cd));
                    play.setOnClickListener(x -> play(item));
                    View delete = v.findViewById(R.id.btnDelete);
                    delete.setContentDescription(getString(R.string.evidence_delete_cd));
                    delete.setOnClickListener(x -> confirmDelete(item));
                    list.addView(v);
                }
            });
        });
    }

    private void play(EvidenceItem item) {
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(item.filePath);
            player.setOnCompletionListener(mp -> stopPlayback());
            player.prepare();
            player.start();
        } catch (IOException | RuntimeException e) {
            stopPlayback();
            Toast.makeText(this, R.string.evidence_play_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void confirmDelete(EvidenceItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.evidence_delete_title)
                .setMessage(R.string.evidence_delete_message)
                .setPositiveButton(R.string.common_delete, (d, w) -> {
                    stopPlayback();
                    Context app = getApplicationContext();
                    AppExecutors.io(() -> {
                        new EvidenceRepository(app).delete(userId, item);
                        AppExecutors.main(this::loadRecordings);
                    });
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        releaseRecorder();
        stopPlayback();
    }
}
