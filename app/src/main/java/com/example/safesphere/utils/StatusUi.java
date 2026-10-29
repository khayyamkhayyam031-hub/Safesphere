package com.example.safesphere.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;

/**
 * Maps status constants to a visible label and chip colours. The label always carries the
 * meaning, so status never depends on colour alone.
 */
public final class StatusUi {

    public enum Tone { SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

    private StatusUi() {
    }

    public static void apply(TextView chip, String status) {
        apply(chip, chip.getContext().getString(labelFor(status)), toneFor(status));
    }

    public static void apply(TextView chip, CharSequence label, Tone tone) {
        Context c = chip.getContext();
        @ColorRes int bg;
        @ColorRes int fg;
        switch (tone) {
            case SUCCESS: bg = R.color.success_light; fg = R.color.success_text; break;
            case WARNING: bg = R.color.warning_light; fg = R.color.warning_text; break;
            case DANGER: bg = R.color.emergency_red_light; fg = R.color.emergency_red_text; break;
            case INFO: bg = R.color.primary_light; fg = R.color.primary_dark; break;
            default: bg = R.color.neutral_chip_bg; fg = R.color.neutral_chip_text; break;
        }
        chip.setText(label);
        chip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
        chip.setTextColor(ContextCompat.getColor(c, fg));
    }

    @StringRes
    public static int labelFor(String status) {
        if (status == null) return R.string.status_not_started;
        switch (status) {
            case SafetyEvent.OP_PENDING: return R.string.status_pending;
            case SafetyEvent.OP_IN_PROGRESS: return R.string.status_in_progress;
            case SafetyEvent.OP_SENT: return R.string.status_sent;
            case SafetyEvent.OP_DELIVERED: return R.string.status_delivered;
            case SafetyEvent.OP_PARTIAL: return R.string.status_partial;
            case SafetyEvent.OP_FAILED: return R.string.status_failed;
            case SafetyEvent.OP_UNAVAILABLE: return R.string.status_unavailable;
            case SafetyEvent.OP_COMPLETED: return R.string.status_completed;
            case SafetyEvent.OP_LAST_KNOWN: return R.string.status_last_known;
            case SafetyEvent.OP_SAVED_LOCALLY: return R.string.status_saved_locally;
            case SafetyEvent.STATUS_ACTIVE: return R.string.status_active;
            case SafetyEvent.STATUS_DELAYED: return R.string.status_delayed;
            case SafetyEvent.STATUS_RESOLVED: return R.string.status_resolved;
            case SafetyEvent.STATUS_CANCELLED: return R.string.status_cancelled;
            default: return R.string.status_not_started;
        }
    }

    public static Tone toneFor(String status) {
        if (status == null) return Tone.NEUTRAL;
        switch (status) {
            case SafetyEvent.OP_SENT:
            case SafetyEvent.OP_DELIVERED:
            case SafetyEvent.OP_COMPLETED:
            case SafetyEvent.OP_SAVED_LOCALLY:
            case SafetyEvent.STATUS_RESOLVED:
                return Tone.SUCCESS;
            case SafetyEvent.OP_PENDING:
            case SafetyEvent.OP_IN_PROGRESS:
            case SafetyEvent.OP_PARTIAL:
            case SafetyEvent.OP_LAST_KNOWN:
            case SafetyEvent.STATUS_DELAYED:
                return Tone.WARNING;
            case SafetyEvent.OP_FAILED:
                return Tone.DANGER;
            case SafetyEvent.STATUS_ACTIVE:
                return Tone.INFO;
            default:
                return Tone.NEUTRAL;
        }
    }

    /** Event status chip; an open SOS is always shown as an emergency. */
    public static void applyEventStatus(TextView chip, SafetyEvent event) {
        if (SafetyEvent.TYPE_SOS.equals(event.type) && event.isOpen()) {
            apply(chip, chip.getContext().getString(R.string.status_emergency_active), Tone.DANGER);
        } else {
            apply(chip, event.status);
        }
    }

    public static String eventTitle(Context c, SafetyEvent e) {
        if (e.type == null) return c.getString(R.string.history_type_other);
        switch (e.type) {
            case SafetyEvent.TYPE_SOS: return c.getString(R.string.history_type_sos);
            case SafetyEvent.TYPE_JOURNEY:
                return e.destination == null || e.destination.isEmpty()
                        ? c.getString(R.string.history_type_journey)
                        : c.getString(R.string.history_type_journey_to, e.destination);
            case SafetyEvent.TYPE_FAKE_CALL: return c.getString(R.string.history_type_fake_call);
            case SafetyEvent.TYPE_EVIDENCE: return c.getString(R.string.history_type_evidence);
            default: return c.getString(R.string.history_type_other);
        }
    }

    @DrawableRes
    public static int eventIcon(SafetyEvent e) {
        if (e.type == null) return R.drawable.ic_info;
        switch (e.type) {
            case SafetyEvent.TYPE_SOS: return R.drawable.ic_warning;
            case SafetyEvent.TYPE_JOURNEY: return R.drawable.ic_journey;
            case SafetyEvent.TYPE_FAKE_CALL: return R.drawable.ic_phone;
            case SafetyEvent.TYPE_EVIDENCE: return R.drawable.ic_mic;
            default: return R.drawable.ic_info;
        }
    }

    public static String triggerLabel(Context c, String trigger) {
        if (SafetyEvent.TRIGGER_SHAKE.equals(trigger)) return c.getString(R.string.trigger_shake);
        if (SafetyEvent.TRIGGER_JOURNEY.equals(trigger)) return c.getString(R.string.trigger_journey);
        return c.getString(R.string.trigger_button);
    }

    /** Short location line for cards, or null when there is no location. */
    public static String locationSummary(Context c, SafetyEvent e) {
        if (!e.hasLocation) return null;
        String coords = String.format(java.util.Locale.US, "%.4f, %.4f", e.latitude, e.longitude);
        return SafetyEvent.OP_LAST_KNOWN.equals(e.locationStatus)
                ? c.getString(R.string.location_summary_last_known, coords)
                : c.getString(R.string.location_summary, coords, Math.round(e.accuracyMeters));
    }
}
