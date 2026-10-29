package com.example.safesphere.sos;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.safesphere.R;
import com.example.safesphere.data.AlertAttempt;
import com.example.safesphere.data.Guardian;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.data.User;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.database.GuardianRepository;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.location.LocationHelper;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the emergency workflow once the countdown completes:
 * <ol>
 *   <li>saves an SOS event in local SQLite,</li>
 *   <li>tries to get a fresh location (falling back to the last known one, clearly labelled),</li>
 *   <li>sends an SMS from the user's own SIM to every saved guardian, Tier 1 first.</li>
 * </ol>
 * Each step records its real outcome. SMS "Sent" means the network accepted it; "Delivered"
 * is only set when the carrier returns a delivery report.
 */
public final class EmergencyManager {

    private static final String TAG = "EmergencyManager";
    private static final long LOCATION_TIMEOUT_MS = 12_000L;

    public interface CreatedCallback {
        /** Main thread. {@code eventId} is -1 if even the local record could not be saved. */
        void onCreated(long eventId);
    }

    private EmergencyManager() {
    }

    public static boolean canSendSms(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Records a cancelled countdown in history. */
    public static void recordCancelled(Context context, int userId, String trigger) {
        Context app = context.getApplicationContext();
        AppExecutors.io(() -> {
            SafetyEvent e = new SafetyEvent();
            e.userId = userId;
            e.type = SafetyEvent.TYPE_SOS;
            e.trigger = trigger;
            e.status = SafetyEvent.STATUS_CANCELLED;
            e.startedAt = System.currentTimeMillis();
            e.endedAt = e.startedAt;
            e.locationStatus = SafetyEvent.OP_NOT_STARTED;
            e.alertStatus = SafetyEvent.OP_NOT_STARTED;
            e.evidenceStatus = SafetyEvent.OP_NOT_STARTED;
            new EventRepository(app).insert(e);
        });
    }

    public static void activate(Context context, int userId, String trigger, CreatedCallback callback) {
        Context app = context.getApplicationContext();
        AppExecutors.io(() -> {
            EventRepository events = new EventRepository(app);
            List<Guardian> guardians = new GuardianRepository(app).getGuardians(userId);
            boolean smsPossible = canSendSms(app);
            boolean locationPossible = LocationHelper.hasPermission(app) && LocationHelper.isLocationEnabled(app);

            SafetyEvent event = new SafetyEvent();
            event.userId = userId;
            event.type = SafetyEvent.TYPE_SOS;
            event.trigger = trigger;
            event.status = SafetyEvent.STATUS_ACTIVE;
            event.startedAt = System.currentTimeMillis();
            event.locationStatus = locationPossible ? SafetyEvent.OP_IN_PROGRESS : SafetyEvent.OP_UNAVAILABLE;
            event.alertStatus = (!guardians.isEmpty() && smsPossible)
                    ? SafetyEvent.OP_PENDING : SafetyEvent.OP_UNAVAILABLE;
            event.evidenceStatus = SafetyEvent.OP_NOT_STARTED;
            long eventId = events.insert(event);
            AppExecutors.main(() -> callback.onCreated(eventId));
            if (eventId <= 0) return;

            Runnable sendAlerts = () -> AppExecutors.io(() -> {
                SafetyEvent latest = events.getEvent(eventId);
                if (latest == null || !latest.isOpen()) return; // deleted or resolved meanwhile
                if (SafetyEvent.OP_UNAVAILABLE.equals(latest.alertStatus)) return;
                sendSmsAlerts(app, events, latest, guardians);
            });

            if (!locationPossible) {
                sendAlerts.run();
                return;
            }
            LocationHelper.requestFresh(app, LOCATION_TIMEOUT_MS, (location, fresh) -> AppExecutors.io(() -> {
                SafetyEvent latest = events.getEvent(eventId);
                if (latest == null) return;
                applyLocation(latest, location, fresh);
                events.update(latest);
                sendAlerts.run();
            }));
        });
    }

    static void applyLocation(SafetyEvent event, @Nullable Location location, boolean fresh) {
        if (location == null) {
            event.hasLocation = false;
            event.locationStatus = SafetyEvent.OP_FAILED;
            return;
        }
        event.hasLocation = true;
        event.latitude = location.getLatitude();
        event.longitude = location.getLongitude();
        event.accuracyMeters = location.getAccuracy();
        event.locationTime = location.getTime();
        event.locationStatus = fresh ? SafetyEvent.OP_COMPLETED : SafetyEvent.OP_LAST_KNOWN;
    }

    private static void sendSmsAlerts(Context app, EventRepository events, SafetyEvent event,
                                      List<Guardian> guardians) {
        User user = new UserRepository(app).getUserById(event.userId);
        String name = user != null ? user.getFirstName() : app.getString(R.string.sos_sms_default_name);
        String message = buildMessage(app, name, event);

        SmsManager sms;
        try {
            sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? app.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
        } catch (RuntimeException e) {
            sms = null;
        }

        for (Guardian g : guardians) {
            AlertAttempt attempt = new AlertAttempt();
            attempt.eventId = event.id;
            attempt.guardianName = g.name;
            attempt.phoneNumber = g.phoneNumber;
            attempt.status = SafetyEvent.OP_PENDING;
            if (events.insertAttempt(attempt) <= 0) continue;

            if (sms == null) {
                events.updateAttemptStatus(attempt.id, SafetyEvent.OP_FAILED,
                        app.getString(R.string.sos_error_sms_unavailable));
                continue;
            }
            try {
                ArrayList<String> parts = sms.divideMessage(message);
                ArrayList<PendingIntent> sent = new ArrayList<>();
                ArrayList<PendingIntent> delivered = new ArrayList<>();
                PendingIntent sentPi = statusIntent(app, SmsStatusReceiver.ACTION_SENT, attempt.id, 0);
                PendingIntent deliveredPi = statusIntent(app, SmsStatusReceiver.ACTION_DELIVERED, attempt.id, 1);
                for (int i = 0; i < parts.size(); i++) {
                    sent.add(sentPi);
                    // Only the last part's report means the whole message arrived.
                    delivered.add(i == parts.size() - 1 ? deliveredPi : null);
                }
                if (parts.size() == 1) {
                    sms.sendTextMessage(g.phoneNumber, null, message, sentPi, deliveredPi);
                } else {
                    sms.sendMultipartTextMessage(g.phoneNumber, null, parts, sent, delivered);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "SMS hand-off failed", e);
                events.updateAttemptStatus(attempt.id, SafetyEvent.OP_FAILED,
                        app.getString(R.string.sos_error_sms_rejected));
            }
        }

        events.refreshAlertSummary(event.id);
    }

    static String buildMessage(Context app, String name, SafetyEvent event) {
        return app.getString(R.string.sos_sms_message, name, locationText(app, event));
    }

    /** Draft for the user's own SMS app when automatic alerts are not possible. */
    static String buildManualMessage(Context app, SafetyEvent event) {
        return app.getString(R.string.sos_sms_manual_message, locationText(app, event));
    }

    private static String locationText(Context app, SafetyEvent event) {
        String location;
        if (event.hasLocation) {
            String url = LocationHelper.mapsUrl(event.latitude, event.longitude);
            String time = TimeUtils.formatTime(app, event.locationTime);
            int accuracy = Math.round(event.accuracyMeters);
            location = SafetyEvent.OP_LAST_KNOWN.equals(event.locationStatus)
                    ? app.getString(R.string.sos_sms_location_last_known, url, accuracy, time)
                    : app.getString(R.string.sos_sms_location, url, accuracy, time);
        } else {
            location = app.getString(R.string.sos_sms_location_unavailable);
        }
        return location;
    }

    private static PendingIntent statusIntent(Context app, String action, long attemptId, int offset) {
        Intent intent = new Intent(app, SmsStatusReceiver.class)
                .setAction(action)
                .putExtra(SmsStatusReceiver.EXTRA_ATTEMPT_ID, attemptId);
        int requestCode = (int) (attemptId * 2 + offset);
        return PendingIntent.getBroadcast(app, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void resolve(Context context, long eventId, Runnable onDone) {
        Context app = context.getApplicationContext();
        AppExecutors.io(() -> {
            EventRepository events = new EventRepository(app);
            SafetyEvent e = events.getEvent(eventId);
            if (e != null && e.isOpen()) {
                e.status = SafetyEvent.STATUS_RESOLVED;
                e.endedAt = System.currentTimeMillis();
                events.update(e);
            }
            AppExecutors.main(onDone);
        });
    }
}
