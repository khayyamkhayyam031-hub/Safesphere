package com.example.safesphere.home;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.data.User;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.database.GuardianRepository;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.data.Guardian;
import com.example.safesphere.evidence.EvidenceActivity;
import com.example.safesphere.fakecall.FakeCallActivity;
import com.example.safesphere.helplines.EmergencyHelplinesActivity;
import com.example.safesphere.history.HistoryAdapter;
import com.example.safesphere.history.HistoryDetailsActivity;
import com.example.safesphere.location.LiveLocationActivity;
import com.example.safesphere.location.LocationHelper;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.settings.SafetyPreferences;
import com.example.safesphere.sos.EmergencyManager;
import com.example.safesphere.sos.EmergencyStatusActivity;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.EmptyState;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final long HOLD_DURATION_MS = 2000L;
    /** A cached fix older than this is labelled as stale. */
    private static final long STALE_LOCATION_MS = 5 * 60 * 1000L;

    private View root;
    private CircularProgressIndicator sosProgress;
    private TextView tvSosHint;
    private ValueAnimator holdAnimator;
    private boolean holdCompleted;

    private int userId;
    @Nullable
    private SafetyEvent openSos;
    @Nullable
    private SafetyEvent openJourney;
    @Nullable
    private Location shownLocation;
    private boolean shownLocationFresh;
    private boolean refreshingLocation;
    private Runnable cancelLocation;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_home, container, false);
        userId = new SessionManager(requireContext()).getUserId();

        sosProgress = root.findViewById(R.id.sosProgress);
        tvSosHint = root.findViewById(R.id.tvSosHint);
        setUpSosButton(root.findViewById(R.id.btnSos));

        root.findViewById(R.id.btnSettings).setOnClickListener(v -> selectTab(R.id.nav_settings));
        root.findViewById(R.id.btnSeeAll).setOnClickListener(v -> selectTab(R.id.nav_history));
        root.findViewById(R.id.cardActiveSos).setOnClickListener(v -> {
            if (openSos != null) startActivity(EmergencyStatusActivity.intent(requireContext(), openSos.id));
        });
        root.findViewById(R.id.cardActiveJourney).setOnClickListener(v -> selectTab(R.id.nav_journey));

        bindQuickAction(R.id.qaJourney, R.drawable.ic_journey, R.string.home_safe_journey, v -> selectTab(R.id.nav_journey));
        bindQuickAction(R.id.qaGuardians, R.drawable.ic_guardians, R.string.home_guardians, v -> selectTab(R.id.nav_guardians));
        bindQuickAction(R.id.qaFakeCall, R.drawable.ic_phone, R.string.home_fake_call,
                v -> startActivity(new Intent(requireContext(), FakeCallActivity.class)));
        bindQuickAction(R.id.qaHelplines, R.drawable.ic_helpline, R.string.home_helplines,
                v -> startActivity(new Intent(requireContext(), EmergencyHelplinesActivity.class)));
        bindQuickAction(R.id.qaLocation, R.drawable.ic_location, R.string.home_live_location,
                v -> startActivity(new Intent(requireContext(), LiveLocationActivity.class)));
        bindQuickAction(R.id.qaEvidence, R.drawable.ic_mic, R.string.home_evidence,
                v -> startActivity(new Intent(requireContext(), EvidenceActivity.class)));

        root.findViewById(R.id.btnOpenMap).setOnClickListener(v -> {
            if (shownLocation != null) {
                UiUtils.openMap(requireContext(), shownLocation.getLatitude(), shownLocation.getLongitude());
            }
        });
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refresh();
        else resetHold();
    }

    @Override
    public void onPause() {
        super.onPause();
        resetHold();
        if (cancelLocation != null) cancelLocation.run();
        refreshingLocation = false;
    }

    private void selectTab(int id) {
        MainActivity main = MainActivity.from(this);
        if (main != null) main.selectTab(id);
    }

    private void bindQuickAction(int includeId, @DrawableRes int icon, @StringRes int label, View.OnClickListener l) {
        View tile = root.findViewById(includeId);
        ((ImageView) tile.findViewById(R.id.ivIcon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.tvLabel)).setText(label);
        tile.setOnClickListener(l);
    }

    // ---------- SOS hold-to-activate ----------

    @SuppressLint("ClickableViewAccessibility")
    private void setUpSosButton(View button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    startHold(v);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (!holdCompleted) {
                        resetHold();
                        tvSosHint.setText(R.string.home_sos_released_early);
                    }
                    return true;
                default:
                    return true;
            }
        });
        // Screen-reader users activate via double-tap; the countdown still lets them cancel.
        button.setOnClickListener(v -> {
            if (isTouchExplorationEnabled()) {
                triggerSos();
            } else {
                tvSosHint.setText(R.string.home_sos_hold);
            }
        });
    }

    private boolean isTouchExplorationEnabled() {
        AccessibilityManager am = (AccessibilityManager) requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isTouchExplorationEnabled();
    }

    private void startHold(View button) {
        resetHold();
        holdCompleted = false;
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        tvSosHint.setText(R.string.home_sos_keep_holding);
        holdAnimator = ValueAnimator.ofInt(0, sosProgress.getMax());
        holdAnimator.setDuration(HOLD_DURATION_MS);
        holdAnimator.setInterpolator(new LinearInterpolator());
        holdAnimator.addUpdateListener(a -> sosProgress.setProgressCompat((int) a.getAnimatedValue(), false));
        holdAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled || !isAdded()) return;
                holdCompleted = true;
                button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                triggerSos();
                button.postDelayed(() -> resetHold(), 300);
            }
        });
        holdAnimator.start();
    }

    private void resetHold() {
        if (holdAnimator != null) {
            holdAnimator.cancel();
            holdAnimator = null;
        }
        if (sosProgress != null) sosProgress.setProgressCompat(0, true);
        if (tvSosHint != null && holdCompleted) tvSosHint.setText(R.string.home_sos_hold);
        holdCompleted = false;
    }

    private void triggerSos() {
        if (openSos != null) {
            startActivity(EmergencyStatusActivity.intent(requireContext(), openSos.id));
            return;
        }
        MainActivity main = MainActivity.from(this);
        if (main != null) main.startSos(SafetyEvent.TRIGGER_BUTTON);
    }

    // ---------- Data ----------

    private void refresh() {
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            User user = new UserRepository(app).getUserById(userId);
            GuardianRepository guardians = new GuardianRepository(app);
            int total = guardians.countGuardians(userId, 0);
            int tier1 = guardians.countGuardians(userId, Guardian.TIER_PRIMARY);
            EventRepository events = new EventRepository(app);
            SafetyEvent sos = events.getOpenEvent(userId, SafetyEvent.TYPE_SOS);
            SafetyEvent journey = events.getOpenEvent(userId, SafetyEvent.TYPE_JOURNEY);
            List<SafetyEvent> recent = events.getEvents(userId, EventRepository.FILTER_ALL, 3);
            AppExecutors.main(() -> {
                if (!isAdded() || root == null) return;
                bindHeader(user);
                openSos = sos;
                openJourney = journey;
                bindActiveCards();
                bindStatus(total, tier1);
                bindRecent(recent);
                if (!refreshingLocation) bindLocation(LocationHelper.getLastKnown(requireContext()), false);
            });
        });
    }

    private void bindHeader(@Nullable User user) {
        String first = user != null ? user.getFirstName() : "";
        ((TextView) root.findViewById(R.id.tvGreeting)).setText(first.isEmpty()
                ? getString(R.string.home_greeting_default)
                : getString(R.string.home_greeting, first));
        UiUtils.bindAvatar(root.findViewById(R.id.ivAvatar), root.findViewById(R.id.tvAvatarInitial),
                user != null ? user.profilePhotoPath : null, user != null ? user.getInitial() : "?");
    }

    private void bindActiveCards() {
        View sosCard = root.findViewById(R.id.cardActiveSos);
        if (openSos != null) {
            sosCard.setVisibility(View.VISIBLE);
            ((TextView) root.findViewById(R.id.tvActiveSosDetail)).setText(getString(
                    R.string.home_sos_active_detail, TimeUtils.formatTime(requireContext(), openSos.startedAt)));
        } else {
            sosCard.setVisibility(View.GONE);
        }

        View journeyCard = root.findViewById(R.id.cardActiveJourney);
        if (openJourney != null) {
            journeyCard.setVisibility(View.VISIBLE);
            ((TextView) root.findViewById(R.id.tvActiveJourneyTitle)).setText(
                    getString(R.string.home_journey_active_title, openJourney.destination));
            boolean late = System.currentTimeMillis() > openJourney.expectedArrival;
            ((TextView) root.findViewById(R.id.tvActiveJourneyDetail)).setText(late
                    ? getString(R.string.home_journey_late)
                    : getString(R.string.home_journey_eta, TimeUtils.formatTime(requireContext(), openJourney.expectedArrival)));
        } else {
            journeyCard.setVisibility(View.GONE);
        }

        ((TextView) root.findViewById(R.id.tvSosSubHint)).setText(openSos != null
                ? getString(R.string.home_sos_sub_active)
                : getString(R.string.home_sos_sub_countdown, new SafetyPreferences(requireContext()).getCountdownSeconds()));
    }

    private void bindStatus(int guardianCount, int tier1Count) {
        Context c = requireContext();
        boolean smsGranted = UiUtils.hasPermission(c, Manifest.permission.SEND_SMS);
        boolean locationGranted = LocationHelper.hasPermission(c);
        boolean locationOn = LocationHelper.isLocationEnabled(c);
        boolean hasTelephony = c.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY);

        // Guardians
        bindRow(R.id.rowGuardians, R.drawable.ic_guardians, getString(R.string.status_row_guardians),
                guardianCount == 0 ? getString(R.string.status_guardians_none)
                        : getResources().getQuantityString(R.plurals.status_guardians_count, guardianCount, guardianCount, tier1Count),
                guardianCount == 0 ? getString(R.string.status_chip_missing) : getString(R.string.status_chip_ready),
                guardianCount == 0 ? StatusUi.Tone.WARNING : StatusUi.Tone.SUCCESS);

        // Location
        String locDetail;
        String locChip;
        StatusUi.Tone locTone;
        if (!locationGranted) {
            locDetail = getString(R.string.status_location_no_permission);
            locChip = getString(R.string.status_chip_off);
            locTone = StatusUi.Tone.WARNING;
        } else if (!locationOn) {
            locDetail = getString(R.string.status_location_disabled);
            locChip = getString(R.string.status_chip_off);
            locTone = StatusUi.Tone.WARNING;
        } else {
            locDetail = getString(R.string.status_location_ready);
            locChip = getString(R.string.status_chip_ready);
            locTone = StatusUi.Tone.SUCCESS;
        }
        bindRow(R.id.rowLocation, R.drawable.ic_location, getString(R.string.status_row_location), locDetail, locChip, locTone);

        // SMS alerts
        String smsDetail;
        String smsChip;
        StatusUi.Tone smsTone;
        if (!hasTelephony) {
            smsDetail = getString(R.string.status_sms_no_telephony);
            smsChip = getString(R.string.status_unavailable);
            smsTone = StatusUi.Tone.NEUTRAL;
        } else if (!smsGranted) {
            smsDetail = getString(R.string.status_sms_no_permission);
            smsChip = getString(R.string.status_chip_off);
            smsTone = StatusUi.Tone.WARNING;
        } else if (guardianCount == 0) {
            smsDetail = getString(R.string.status_sms_no_guardians);
            smsChip = getString(R.string.status_chip_missing);
            smsTone = StatusUi.Tone.WARNING;
        } else {
            smsDetail = getString(R.string.status_sms_ready);
            smsChip = getString(R.string.status_chip_ready);
            smsTone = StatusUi.Tone.SUCCESS;
        }
        bindRow(R.id.rowSms, R.drawable.ic_sms, getString(R.string.status_row_sms), smsDetail, smsChip, smsTone);

        boolean shake = new SafetyPreferences(c).isShakeSosEnabled();
        bindRow(R.id.rowShake, R.drawable.ic_refresh, getString(R.string.status_row_shake),
                shake ? getString(R.string.status_shake_on) : getString(R.string.status_shake_off),
                shake ? getString(R.string.settings_on) : getString(R.string.settings_off),
                shake ? StatusUi.Tone.SUCCESS : StatusUi.Tone.NEUTRAL);

        // One clear next step, in priority order.
        Button fix = root.findViewById(R.id.btnFixSetup);
        if (guardianCount == 0) {
            fix.setVisibility(View.VISIBLE);
            fix.setText(R.string.home_fix_add_guardian);
            fix.setOnClickListener(v -> selectTab(R.id.nav_guardians));
        } else if ((hasTelephony && !smsGranted) || !locationGranted) {
            fix.setVisibility(View.VISIBLE);
            fix.setText(R.string.home_fix_permissions);
            fix.setOnClickListener(v -> explainAndRequestPermissions());
        } else if (!locationOn) {
            fix.setVisibility(View.VISIBLE);
            fix.setText(R.string.home_fix_location_on);
            fix.setOnClickListener(v -> UiUtils.openLocationSettings(requireContext()));
        } else {
            fix.setVisibility(View.GONE);
        }
    }

    private void bindRow(int includeId, @DrawableRes int icon, String label, String detail, String chip, StatusUi.Tone tone) {
        View row = root.findViewById(includeId);
        ((ImageView) row.findViewById(R.id.ivIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvDetail)).setText(detail);
        StatusUi.apply(row.findViewById(R.id.tvChip), chip, tone);
    }

    private void bindRecent(List<SafetyEvent> recent) {
        LinearLayout list = root.findViewById(R.id.recentList);
        list.removeAllViews();
        View emptyCard = root.findViewById(R.id.recentEmptyCard);
        if (recent.isEmpty()) {
            emptyCard.setVisibility(View.VISIBLE);
            EmptyState.bind(root.findViewById(R.id.recentEmpty), R.drawable.ic_history,
                    R.string.home_no_activity_title, R.string.home_no_activity, 0, null);
            return;
        }
        emptyCard.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (SafetyEvent e : recent) {
            View item = inflater.inflate(R.layout.item_history, list, false);
            HistoryAdapter.bind(item, e);
            item.setOnClickListener(v -> openEvent(e));
            list.addView(item);
        }
    }

    private void openEvent(SafetyEvent e) {
        if (SafetyEvent.TYPE_SOS.equals(e.type) && e.isOpen()) {
            startActivity(EmergencyStatusActivity.intent(requireContext(), e.id));
        } else if (SafetyEvent.TYPE_JOURNEY.equals(e.type) && e.isOpen()) {
            selectTab(R.id.nav_journey);
        } else {
            startActivity(HistoryDetailsActivity.intent(requireContext(), e.id));
        }
    }

    // ---------- Location card ----------

    private void bindLocation(@Nullable Location location, boolean fresh) {
        Context c = requireContext();
        shownLocation = location;
        shownLocationFresh = fresh;
        TextView value = root.findViewById(R.id.tvLocationValue);
        TextView meta = root.findViewById(R.id.tvLocationMeta);
        TextView chip = root.findViewById(R.id.tvLocationChip);
        Button primary = root.findViewById(R.id.btnLocationPrimary);
        View openMap = root.findViewById(R.id.btnOpenMap);
        openMap.setVisibility(location != null ? View.VISIBLE : View.GONE);
        primary.setEnabled(!refreshingLocation);

        if (!LocationHelper.hasPermission(c)) {
            value.setText(R.string.location_permission_missing);
            meta.setText(R.string.location_permission_reason);
            StatusUi.apply(chip, getString(R.string.status_chip_off), StatusUi.Tone.WARNING);
            primary.setText(R.string.location_allow);
            primary.setOnClickListener(v -> explainAndRequestPermissions());
            return;
        }
        primary.setText(refreshingLocation ? R.string.location_locating : R.string.location_refresh);
        primary.setOnClickListener(v -> refreshLocation());

        if (location == null) {
            value.setText(R.string.location_none_yet);
            meta.setText(LocationHelper.isLocationEnabled(c)
                    ? getString(R.string.location_tap_refresh)
                    : getString(R.string.status_location_disabled));
            StatusUi.apply(chip, getString(R.string.status_unavailable), StatusUi.Tone.NEUTRAL);
            return;
        }
        value.setText(String.format(Locale.US, "%.5f, %.5f", location.getLatitude(), location.getLongitude()));
        long age = System.currentTimeMillis() - location.getTime();
        String when = TimeUtils.relative(c, location.getTime());
        int accuracy = Math.round(location.getAccuracy());
        if (fresh) {
            meta.setText(getString(R.string.location_meta_current, when, accuracy));
            StatusUi.apply(chip, getString(R.string.location_chip_current), StatusUi.Tone.SUCCESS);
        } else {
            meta.setText(getString(R.string.location_meta_last_known, when, accuracy));
            StatusUi.apply(chip, getString(R.string.status_last_known),
                    age > STALE_LOCATION_MS ? StatusUi.Tone.WARNING : StatusUi.Tone.NEUTRAL);
        }
    }

    private void refreshLocation() {
        if (refreshingLocation) return;
        if (!LocationHelper.isLocationEnabled(requireContext())) {
            UiUtils.openLocationSettings(requireContext());
            return;
        }
        refreshingLocation = true;
        bindLocation(shownLocation, shownLocationFresh);
        cancelLocation = LocationHelper.requestFresh(requireContext(), 15_000L, (location, fresh) -> {
            refreshingLocation = false;
            if (!isAdded() || root == null) return;
            bindLocation(location, fresh);
            if (!fresh) {
                ((TextView) root.findViewById(R.id.tvLocationMeta)).append("\n" + getString(R.string.location_fresh_failed));
            }
        });
    }

    // ---------- Permissions ----------

    private void explainAndRequestPermissions() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.permission_sos_title)
                .setMessage(R.string.permission_sos_message)
                .setPositiveButton(R.string.common_continue, (d, w) -> permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.SEND_SMS}))
                .setNegativeButton(R.string.common_not_now, null)
                .show();
    }

    private void onPermissionsResult(Map<String, Boolean> result) {
        if (!isAdded()) return;
        boolean anyDenied = false;
        for (Map.Entry<String, Boolean> e : result.entrySet()) {
            if (!e.getValue() && !Manifest.permission.ACCESS_FINE_LOCATION.equals(e.getKey())) anyDenied = true;
        }
        if (anyDenied && !shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)
                && !UiUtils.hasPermission(requireContext(), Manifest.permission.SEND_SMS)) {
            // Permanently denied: the only route left is system settings.
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.permission_denied_title)
                    .setMessage(R.string.permission_denied_message)
                    .setPositiveButton(R.string.common_open_settings, (d, w) -> UiUtils.openAppSettings(requireContext()))
                    .setNegativeButton(R.string.common_not_now, null)
                    .show();
        }
        refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        resetHold();
        root = null;
    }
}
