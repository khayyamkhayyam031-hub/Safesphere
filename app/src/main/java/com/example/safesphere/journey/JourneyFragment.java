package com.example.safesphere.journey;

import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.safesphere.R;
import com.example.safesphere.data.Guardian;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.database.GuardianRepository;
import com.example.safesphere.home.MainActivity;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Safe Journey: set a destination and expected arrival, then check in on arrival. A late
 * journey is marked Delayed and prompts the user, but never raises an SOS on its own.
 * Journeys are tracked on this phone only; route deviation is not implemented.
 */
public class JourneyFragment extends Fragment {

    private static final long MINUTE = 60_000L;
    private static final String STATE_ETA = "chosen_eta";

    private View root;
    private int userId;
    @Nullable
    private SafetyEvent journey;
    private long chosenEta;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            bindActiveTimes();
            ticker.postDelayed(this, 1000L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_journey, container, false);
        userId = new SessionManager(requireContext()).getUserId();
        if (savedInstanceState != null) chosenEta = savedInstanceState.getLong(STATE_ETA);

        ChipGroup chips = root.findViewById(R.id.etaChips);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chip15) setEta(System.currentTimeMillis() + 15 * MINUTE);
            else if (id == R.id.chip30) setEta(System.currentTimeMillis() + 30 * MINUTE);
            else if (id == R.id.chip60) setEta(System.currentTimeMillis() + 60 * MINUTE);
        });
        root.findViewById(R.id.chipCustom).setOnClickListener(v -> pickTime());

        root.findViewById(R.id.btnStart).setOnClickListener(v -> startJourney());
        root.findViewById(R.id.btnArrived).setOnClickListener(v -> finishJourney(SafetyEvent.STATUS_COMPLETED));
        root.findViewById(R.id.btnCancelJourney).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.journey_cancel_title)
                .setMessage(R.string.journey_cancel_message)
                .setPositiveButton(R.string.journey_cancel_confirm, (d, w) -> finishJourney(SafetyEvent.STATUS_CANCELLED))
                .setNegativeButton(R.string.journey_cancel_keep, null)
                .show());
        root.findViewById(R.id.btnExtend).setOnClickListener(v -> extend());
        root.findViewById(R.id.btnJourneySos).setOnClickListener(v -> {
            MainActivity main = MainActivity.from(this);
            if (main != null) main.startSos(SafetyEvent.TRIGGER_JOURNEY);
        });
        root.findViewById(R.id.btnShare).setOnClickListener(v -> shareBySms());
        bindEtaText();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) load();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) ticker.removeCallbacks(tick);
        else load();
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_ETA, chosenEta);
    }

    private void load() {
        root.findViewById(R.id.pbLoading).setVisibility(View.VISIBLE);
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            SafetyEvent open = new EventRepository(app).getOpenEvent(userId, SafetyEvent.TYPE_JOURNEY);
            AppExecutors.main(() -> {
                if (!isAdded() || root == null) return;
                root.findViewById(R.id.pbLoading).setVisibility(View.GONE);
                journey = open;
                bind();
            });
        });
    }

    private void bind() {
        boolean active = journey != null;
        root.findViewById(R.id.setupGroup).setVisibility(active ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.activeGroup).setVisibility(active ? View.VISIBLE : View.GONE);
        ticker.removeCallbacks(tick);
        if (!active) return;

        ((TextView) root.findViewById(R.id.tvDestination)).setText(journey.destination);
        TextView route = root.findViewById(R.id.tvRoute);
        route.setVisibility(journey.notes == null ? View.GONE : View.VISIBLE);
        route.setText(journey.notes);
        ticker.post(tick);
    }

    private void bindActiveTimes() {
        if (journey == null || root == null) return;
        long now = System.currentTimeMillis();
        boolean late = now > journey.expectedArrival;
        ((TextView) root.findViewById(R.id.tvEta)).setText(TimeUtils.formatTime(requireContext(), journey.expectedArrival));
        ((TextView) root.findViewById(R.id.tvElapsed)).setText(TimeUtils.formatDuration(now - journey.startedAt));
        ((TextView) root.findViewById(R.id.tvRemainingLabel)).setText(late ? R.string.journey_overdue : R.string.journey_remaining);
        ((TextView) root.findViewById(R.id.tvRemaining)).setText(TimeUtils.formatDuration(Math.abs(journey.expectedArrival - now)));
        root.findViewById(R.id.delayedBanner).setVisibility(late ? View.VISIBLE : View.GONE);

        if (late && SafetyEvent.STATUS_ACTIVE.equals(journey.status)) {
            journey.status = SafetyEvent.STATUS_DELAYED;
            persist(journey);
        }
        StatusUi.apply(root.findViewById(R.id.tvStatusChip), journey.status);
    }

    // ---------- Setup ----------

    private void setEta(long eta) {
        chosenEta = eta;
        bindEtaText();
    }

    private void bindEtaText() {
        TextView tv = root.findViewById(R.id.tvEtaChosen);
        if (chosenEta <= System.currentTimeMillis()) {
            tv.setText(R.string.journey_eta_hint);
        } else {
            tv.setText(getString(R.string.journey_eta_chosen, TimeUtils.formatTime(requireContext(), chosenEta),
                    (chosenEta - System.currentTimeMillis() + MINUTE - 1) / MINUTE));
        }
    }

    private void pickTime() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, 30);
        new TimePickerDialog(requireContext(), (view, hour, minute) -> {
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute);
            target.set(Calendar.SECOND, 0);
            if (target.getTimeInMillis() <= System.currentTimeMillis()) target.add(Calendar.DAY_OF_MONTH, 1);
            setEta(target.getTimeInMillis());
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(requireContext())).show();
    }

    private void startJourney() {
        TextInputLayout tilDest = root.findViewById(R.id.tilDestination);
        String destination = text(tilDest).trim();
        String route = text(root.findViewById(R.id.tilRoute)).trim();
        tilDest.setError(null);
        boolean valid = true;
        if (destination.isEmpty()) {
            tilDest.setError(getString(R.string.journey_error_destination));
            valid = false;
        }
        if (chosenEta <= System.currentTimeMillis()) {
            ((TextView) root.findViewById(R.id.tvEtaChosen)).setText(R.string.journey_error_eta);
            valid = false;
        }
        if (!valid) return;

        SafetyEvent e = new SafetyEvent();
        e.userId = userId;
        e.type = SafetyEvent.TYPE_JOURNEY;
        e.status = SafetyEvent.STATUS_ACTIVE;
        e.startedAt = System.currentTimeMillis();
        e.destination = destination;
        e.expectedArrival = chosenEta;
        e.notes = route.isEmpty() ? null : route;
        Context app = requireContext().getApplicationContext();
        root.findViewById(R.id.btnStart).setEnabled(false);
        AppExecutors.io(() -> {
            long id = new EventRepository(app).insert(e);
            AppExecutors.main(() -> {
                if (!isAdded() || root == null) return;
                root.findViewById(R.id.btnStart).setEnabled(true);
                if (id <= 0) {
                    snack(R.string.error_save_failed);
                    return;
                }
                tilDest.getEditText().setText(null);
                ((TextInputLayout) root.findViewById(R.id.tilRoute)).getEditText().setText(null);
                ((ChipGroup) root.findViewById(R.id.etaChips)).clearCheck();
                chosenEta = 0;
                bindEtaText();
                journey = e;
                bind();
            });
        });
    }

    // ---------- Active ----------

    private void extend() {
        if (journey == null) return;
        journey.expectedArrival = Math.max(journey.expectedArrival, System.currentTimeMillis()) + 15 * MINUTE;
        journey.status = SafetyEvent.STATUS_ACTIVE;
        persist(journey);
        bindActiveTimes();
        snack(R.string.journey_extended);
    }

    private void finishJourney(String status) {
        if (journey == null) return;
        SafetyEvent e = journey;
        e.status = status;
        e.endedAt = System.currentTimeMillis();
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            boolean ok = new EventRepository(app).update(e);
            AppExecutors.main(() -> {
                if (!isAdded() || root == null) return;
                if (!ok) {
                    snack(R.string.error_save_failed);
                    load();
                    return;
                }
                snack(SafetyEvent.STATUS_COMPLETED.equals(status) ? R.string.journey_completed_msg : R.string.journey_cancelled_msg);
                journey = null;
                bind();
            });
        });
    }

    private void persist(SafetyEvent e) {
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> new EventRepository(app).update(e));
    }

    /** Opens the SMS app with a draft for Tier 1 guardians; the user decides whether to send. */
    private void shareBySms() {
        if (journey == null) return;
        SafetyEvent e = journey;
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            List<Guardian> all = new GuardianRepository(app).getGuardians(userId);
            List<String> numbers = new ArrayList<>();
            for (Guardian g : all) if (g.tier == Guardian.TIER_PRIMARY) numbers.add(g.phoneNumber);
            if (numbers.isEmpty()) for (Guardian g : all) numbers.add(g.phoneNumber);
            AppExecutors.main(() -> {
                if (!isAdded()) return;
                String body = getString(R.string.journey_share_body, e.destination,
                        TimeUtils.formatTime(requireContext(), e.expectedArrival));
                UiUtils.composeSms(requireContext(), android.text.TextUtils.join(";", numbers), body);
            });
        });
    }

    private void snack(int message) {
        if (getView() != null) Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
    }

    private static String text(TextInputLayout til) {
        return til.getEditText() == null || til.getEditText().getText() == null ? "" : til.getEditText().getText().toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ticker.removeCallbacks(tick);
        root = null;
    }
}
