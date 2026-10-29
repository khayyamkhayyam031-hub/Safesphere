package com.example.safesphere.database;

import static com.example.safesphere.database.SafeHerDatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.safesphere.data.Guardian;

import java.util.ArrayList;
import java.util.List;

/** Trusted guardians stored on this device. Call off the main thread. */
@WorkerThread
public class GuardianRepository {

    private static final String TAG = "GuardianRepository";

    public enum SaveResult { SUCCESS, DUPLICATE, ERROR }

    private static final String[] COLUMNS = {COLUMN_ID, COLUMN_USER_ID, COLUMN_NAME,
            COLUMN_PHONE_NUMBER, COLUMN_RELATIONSHIP, COLUMN_TIER, COLUMN_INVITE_STATUS,
            COLUMN_CREATED_AT, COLUMN_UPDATED_AT};

    private final SafeHerDatabaseHelper dbHelper;

    public GuardianRepository(Context context) {
        dbHelper = SafeHerDatabaseHelper.getInstance(context);
    }

    /** Tier 1 first, then by name. */
    public List<Guardian> getGuardians(int userId) {
        List<Guardian> list = new ArrayList<>();
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_GUARDIANS, COLUMNS,
                COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)}, null, null,
                COLUMN_TIER + " ASC, " + COLUMN_NAME + " COLLATE NOCASE ASC")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    @Nullable
    public Guardian getGuardian(int userId, long guardianId) {
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_GUARDIANS, COLUMNS,
                COLUMN_USER_ID + " = ? AND " + COLUMN_ID + " = ?",
                new String[]{String.valueOf(userId), String.valueOf(guardianId)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public int countGuardians(int userId, int tier) {
        String selection = COLUMN_USER_ID + " = ?" + (tier > 0 ? " AND " + COLUMN_TIER + " = ?" : "");
        String[] args = tier > 0
                ? new String[]{String.valueOf(userId), String.valueOf(tier)}
                : new String[]{String.valueOf(userId)};
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_GUARDIANS,
                new String[]{"COUNT(*)"}, selection, args, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Inserts when {@code guardian.id == 0}, otherwise updates. */
    public SaveResult save(Guardian guardian) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put(COLUMN_NAME, guardian.name.trim());
        v.put(COLUMN_PHONE_NUMBER, guardian.phoneNumber);
        v.put(COLUMN_RELATIONSHIP, guardian.relationship);
        v.put(COLUMN_TIER, guardian.tier);
        v.put(COLUMN_UPDATED_AT, now);
        try {
            if (guardian.id == 0) {
                v.put(COLUMN_USER_ID, guardian.userId);
                v.put(COLUMN_INVITE_STATUS, Guardian.INVITE_NOT_INVITED);
                v.put(COLUMN_CREATED_AT, now);
                guardian.id = dbHelper.getWritableDatabase().insertOrThrow(TABLE_GUARDIANS, null, v);
                return SaveResult.SUCCESS;
            }
            int rows = dbHelper.getWritableDatabase().update(TABLE_GUARDIANS, v,
                    COLUMN_ID + " = ? AND " + COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(guardian.id), String.valueOf(guardian.userId)});
            return rows == 1 ? SaveResult.SUCCESS : SaveResult.ERROR;
        } catch (SQLiteConstraintException e) {
            return SaveResult.DUPLICATE;
        } catch (SQLException e) {
            Log.e(TAG, "Guardian save failed", e);
            return SaveResult.ERROR;
        }
    }

    public boolean delete(int userId, long guardianId) {
        try {
            return dbHelper.getWritableDatabase().delete(TABLE_GUARDIANS,
                    COLUMN_ID + " = ? AND " + COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(guardianId), String.valueOf(userId)}) == 1;
        } catch (SQLException e) {
            Log.e(TAG, "Guardian delete failed", e);
            return false;
        }
    }

    private static Guardian read(Cursor c) {
        Guardian g = new Guardian();
        g.id = c.getLong(0);
        g.userId = c.getInt(1);
        g.name = c.getString(2);
        g.phoneNumber = c.getString(3);
        g.relationship = c.isNull(4) ? null : c.getString(4);
        g.tier = c.getInt(5);
        g.inviteStatus = c.getString(6);
        g.createdAt = c.getLong(7);
        g.updatedAt = c.getLong(8);
        return g;
    }
}
