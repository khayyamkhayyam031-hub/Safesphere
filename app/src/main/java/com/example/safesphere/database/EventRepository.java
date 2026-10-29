package com.example.safesphere.database;

import static com.example.safesphere.database.SafeHerDatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.safesphere.data.AlertAttempt;
import com.example.safesphere.data.SafetyEvent;

import java.util.ArrayList;
import java.util.List;

/** Local safety history (SOS, journeys, fake calls, evidence) and SOS alert attempts. */
@WorkerThread
public class EventRepository {

    private static final String TAG = "EventRepository";

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_OTHER = "OTHER";

    private static final String[] COLUMNS = {COLUMN_ID, COLUMN_USER_ID, COLUMN_TYPE, COLUMN_TRIGGER,
            COLUMN_STATUS, COLUMN_STARTED_AT, COLUMN_ENDED_AT, COLUMN_LATITUDE, COLUMN_LONGITUDE,
            COLUMN_ACCURACY, COLUMN_LOCATION_TIME, COLUMN_LOCATION_STATUS, COLUMN_ALERT_STATUS,
            COLUMN_EVIDENCE_STATUS, COLUMN_DESTINATION, COLUMN_EXPECTED_ARRIVAL, COLUMN_NOTES};

    private final SafeHerDatabaseHelper dbHelper;

    public EventRepository(Context context) {
        dbHelper = SafeHerDatabaseHelper.getInstance(context);
    }

    /** Returns the new id, or -1 on failure. */
    public long insert(SafetyEvent e) {
        try {
            e.id = dbHelper.getWritableDatabase().insertOrThrow(TABLE_EVENTS, null, toValues(e, true));
            return e.id;
        } catch (SQLException ex) {
            Log.e(TAG, "Event insert failed", ex);
            return -1;
        }
    }

    public boolean update(SafetyEvent e) {
        try {
            return dbHelper.getWritableDatabase().update(TABLE_EVENTS, toValues(e, false),
                    COLUMN_ID + " = ?", new String[]{String.valueOf(e.id)}) == 1;
        } catch (SQLException ex) {
            Log.e(TAG, "Event update failed", ex);
            return false;
        }
    }

    @Nullable
    public SafetyEvent getEvent(long eventId) {
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_EVENTS, COLUMNS,
                COLUMN_ID + " = ?", new String[]{String.valueOf(eventId)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Most recent first. {@code filter} is a type constant, {@link #FILTER_ALL} or {@link #FILTER_OTHER}. */
    public List<SafetyEvent> getEvents(int userId, String filter, int limit) {
        String selection = COLUMN_USER_ID + " = ?";
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(userId));
        if (FILTER_OTHER.equals(filter)) {
            selection += " AND " + COLUMN_TYPE + " NOT IN (?, ?)";
            args.add(SafetyEvent.TYPE_SOS);
            args.add(SafetyEvent.TYPE_JOURNEY);
        } else if (filter != null && !FILTER_ALL.equals(filter)) {
            selection += " AND " + COLUMN_TYPE + " = ?";
            args.add(filter);
        }
        List<SafetyEvent> list = new ArrayList<>();
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_EVENTS, COLUMNS, selection,
                args.toArray(new String[0]), null, null, COLUMN_STARTED_AT + " DESC",
                limit > 0 ? String.valueOf(limit) : null)) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    /** The open SOS or journey, if any. */
    @Nullable
    public SafetyEvent getOpenEvent(int userId, String type) {
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_EVENTS, COLUMNS,
                COLUMN_USER_ID + " = ? AND " + COLUMN_TYPE + " = ? AND " + COLUMN_STATUS + " IN (?, ?)",
                new String[]{String.valueOf(userId), type, SafetyEvent.STATUS_ACTIVE,
                        SafetyEvent.STATUS_DELAYED},
                null, null, COLUMN_STARTED_AT + " DESC", "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public boolean delete(int userId, long eventId) {
        try {
            return dbHelper.getWritableDatabase().delete(TABLE_EVENTS,
                    COLUMN_ID + " = ? AND " + COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(eventId), String.valueOf(userId)}) == 1;
        } catch (SQLException ex) {
            Log.e(TAG, "Event delete failed", ex);
            return false;
        }
    }

    /** Clears closed history. Open SOS/journey records are kept. */
    public int deleteClosedHistory(int userId) {
        return dbHelper.getWritableDatabase().delete(TABLE_EVENTS,
                COLUMN_USER_ID + " = ? AND " + COLUMN_STATUS + " NOT IN (?, ?)",
                new String[]{String.valueOf(userId), SafetyEvent.STATUS_ACTIVE, SafetyEvent.STATUS_DELAYED});
    }

    // ---------- Alert attempts ----------

    public long insertAttempt(AlertAttempt a) {
        ContentValues v = new ContentValues();
        v.put(COLUMN_EVENT_ID, a.eventId);
        v.put(COLUMN_GUARDIAN_NAME, a.guardianName);
        v.put(COLUMN_PHONE_NUMBER, a.phoneNumber);
        v.put(COLUMN_STATUS, a.status);
        v.put(COLUMN_ERROR, a.error);
        v.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        try {
            a.id = dbHelper.getWritableDatabase().insertOrThrow(TABLE_ALERT_ATTEMPTS, null, v);
            return a.id;
        } catch (SQLException ex) {
            Log.e(TAG, "Alert attempt insert failed", ex);
            return -1;
        }
    }

    /**
     * Records an SMS callback. A failure is final: a later "sent" for another part of the same
     * multipart message does not overwrite it. "Delivered" only follows "sent".
     */
    public void updateAttemptStatus(long attemptId, String status, @Nullable String error) {
        String guard;
        String[] args;
        if (SafetyEvent.OP_DELIVERED.equals(status)) {
            guard = " AND " + COLUMN_STATUS + " = ?";
            args = new String[]{String.valueOf(attemptId), SafetyEvent.OP_SENT};
        } else {
            guard = " AND " + COLUMN_STATUS + " NOT IN (?, ?)";
            args = new String[]{String.valueOf(attemptId), SafetyEvent.OP_FAILED, SafetyEvent.OP_DELIVERED};
        }
        ContentValues v = new ContentValues();
        v.put(COLUMN_STATUS, status);
        v.put(COLUMN_ERROR, error);
        v.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        try {
            int rows = dbHelper.getWritableDatabase().update(TABLE_ALERT_ATTEMPTS, v,
                    COLUMN_ID + " = ?" + guard, args);
            if (rows > 0) refreshAlertSummaryForAttempt(attemptId);
        } catch (SQLException ex) {
            Log.e(TAG, "Alert attempt update failed", ex);
        }
    }

    public List<AlertAttempt> getAttempts(long eventId) {
        List<AlertAttempt> list = new ArrayList<>();
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_ALERT_ATTEMPTS,
                new String[]{COLUMN_ID, COLUMN_EVENT_ID, COLUMN_GUARDIAN_NAME, COLUMN_PHONE_NUMBER,
                        COLUMN_STATUS, COLUMN_ERROR, COLUMN_UPDATED_AT},
                COLUMN_EVENT_ID + " = ?", new String[]{String.valueOf(eventId)}, null, null,
                COLUMN_ID + " ASC")) {
            while (c.moveToNext()) {
                AlertAttempt a = new AlertAttempt();
                a.id = c.getLong(0);
                a.eventId = c.getLong(1);
                a.guardianName = c.getString(2);
                a.phoneNumber = c.getString(3);
                a.status = c.getString(4);
                a.error = c.isNull(5) ? null : c.getString(5);
                a.updatedAt = c.getLong(6);
                list.add(a);
            }
        }
        return list;
    }

    private void refreshAlertSummaryForAttempt(long attemptId) {
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_ALERT_ATTEMPTS,
                new String[]{COLUMN_EVENT_ID}, COLUMN_ID + " = ?",
                new String[]{String.valueOf(attemptId)}, null, null, null)) {
            if (c.moveToFirst()) refreshAlertSummary(c.getLong(0));
        }
    }

    /** Recomputes the event-level alert status from its attempts. */
    public void refreshAlertSummary(long eventId) {
        synchronized (EventRepository.class) {
            ContentValues v = new ContentValues();
            v.put(COLUMN_ALERT_STATUS, summarize(getAttempts(eventId)));
            dbHelper.getWritableDatabase().update(TABLE_EVENTS, v, COLUMN_ID + " = ?",
                    new String[]{String.valueOf(eventId)});
        }
    }

    public static String summarize(List<AlertAttempt> attempts) {
        if (attempts.isEmpty()) return SafetyEvent.OP_UNAVAILABLE;
        int ok = 0;
        int failed = 0;
        int pending = 0;
        for (AlertAttempt a : attempts) {
            if (SafetyEvent.OP_SENT.equals(a.status) || SafetyEvent.OP_DELIVERED.equals(a.status)) ok++;
            else if (SafetyEvent.OP_FAILED.equals(a.status)) failed++;
            else pending++;
        }
        if (pending > 0) return SafetyEvent.OP_IN_PROGRESS;
        if (failed == attempts.size()) return SafetyEvent.OP_FAILED;
        if (ok == attempts.size()) return SafetyEvent.OP_SENT;
        return SafetyEvent.OP_PARTIAL;
    }

    // ---------- Mapping ----------

    private static ContentValues toValues(SafetyEvent e, boolean includeIdentity) {
        ContentValues v = new ContentValues();
        if (includeIdentity) {
            v.put(COLUMN_USER_ID, e.userId);
            v.put(COLUMN_TYPE, e.type);
            v.put(COLUMN_STARTED_AT, e.startedAt);
            // After insert, alert_status is only written by refreshAlertSummary, so an
            // update carrying a stale copy can never overwrite an SMS callback's result.
            v.put(COLUMN_ALERT_STATUS, e.alertStatus);
        }
        v.put(COLUMN_TRIGGER, e.trigger);
        v.put(COLUMN_STATUS, e.status);
        v.put(COLUMN_ENDED_AT, e.endedAt);
        if (e.hasLocation) {
            v.put(COLUMN_LATITUDE, e.latitude);
            v.put(COLUMN_LONGITUDE, e.longitude);
            v.put(COLUMN_ACCURACY, e.accuracyMeters);
        } else {
            v.putNull(COLUMN_LATITUDE);
            v.putNull(COLUMN_LONGITUDE);
            v.putNull(COLUMN_ACCURACY);
        }
        v.put(COLUMN_LOCATION_TIME, e.locationTime);
        v.put(COLUMN_LOCATION_STATUS, e.locationStatus);
        v.put(COLUMN_EVIDENCE_STATUS, e.evidenceStatus);
        v.put(COLUMN_DESTINATION, e.destination);
        v.put(COLUMN_EXPECTED_ARRIVAL, e.expectedArrival);
        v.put(COLUMN_NOTES, e.notes);
        return v;
    }

    private static SafetyEvent read(Cursor c) {
        SafetyEvent e = new SafetyEvent();
        e.id = c.getLong(0);
        e.userId = c.getInt(1);
        e.type = c.getString(2);
        e.trigger = c.isNull(3) ? null : c.getString(3);
        e.status = c.getString(4);
        e.startedAt = c.getLong(5);
        e.endedAt = c.getLong(6);
        e.hasLocation = !c.isNull(7) && !c.isNull(8);
        if (e.hasLocation) {
            e.latitude = c.getDouble(7);
            e.longitude = c.getDouble(8);
            e.accuracyMeters = c.isNull(9) ? 0f : c.getFloat(9);
        }
        e.locationTime = c.getLong(10);
        e.locationStatus = c.getString(11);
        e.alertStatus = c.getString(12);
        e.evidenceStatus = c.getString(13);
        e.destination = c.isNull(14) ? null : c.getString(14);
        e.expectedArrival = c.getLong(15);
        e.notes = c.isNull(16) ? null : c.getString(16);
        return e;
    }
}
