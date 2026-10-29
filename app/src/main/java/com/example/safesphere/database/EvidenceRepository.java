package com.example.safesphere.database;

import static com.example.safesphere.database.SafeHerDatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.example.safesphere.data.EvidenceItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Metadata for audio evidence; the files themselves live in private app storage. */
@WorkerThread
public class EvidenceRepository {

    private static final String TAG = "EvidenceRepository";

    private final SafeHerDatabaseHelper dbHelper;

    public EvidenceRepository(Context context) {
        dbHelper = SafeHerDatabaseHelper.getInstance(context);
    }

    public long insert(EvidenceItem item) {
        ContentValues v = new ContentValues();
        v.put(COLUMN_USER_ID, item.userId);
        if (item.eventId > 0) v.put(COLUMN_EVENT_ID, item.eventId);
        else v.putNull(COLUMN_EVENT_ID);
        v.put(COLUMN_FILE_PATH, item.filePath);
        v.put(COLUMN_DURATION_MS, item.durationMs);
        v.put(COLUMN_STATUS, item.status);
        v.put(COLUMN_CREATED_AT, item.createdAt);
        try {
            item.id = dbHelper.getWritableDatabase().insertOrThrow(TABLE_EVIDENCE, null, v);
            return item.id;
        } catch (SQLException e) {
            Log.e(TAG, "Evidence insert failed", e);
            return -1;
        }
    }

    public List<EvidenceItem> getAll(int userId) {
        List<EvidenceItem> list = new ArrayList<>();
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_EVIDENCE,
                new String[]{COLUMN_ID, COLUMN_USER_ID, COLUMN_EVENT_ID, COLUMN_FILE_PATH,
                        COLUMN_DURATION_MS, COLUMN_STATUS, COLUMN_CREATED_AT},
                COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)}, null, null,
                COLUMN_CREATED_AT + " DESC")) {
            while (c.moveToNext()) {
                EvidenceItem item = new EvidenceItem();
                item.id = c.getLong(0);
                item.userId = c.getInt(1);
                item.eventId = c.isNull(2) ? 0 : c.getLong(2);
                item.filePath = c.getString(3);
                item.durationMs = c.getLong(4);
                item.status = c.getString(5);
                item.createdAt = c.getLong(6);
                list.add(item);
            }
        }
        return list;
    }

    /** Deletes the row and its file. */
    public boolean delete(int userId, EvidenceItem item) {
        try {
            int rows = dbHelper.getWritableDatabase().delete(TABLE_EVIDENCE,
                    COLUMN_ID + " = ? AND " + COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(item.id), String.valueOf(userId)});
            if (rows == 1) {
                //noinspection ResultOfMethodCallIgnored
                new File(item.filePath).delete();
            }
            return rows == 1;
        } catch (SQLException e) {
            Log.e(TAG, "Evidence delete failed", e);
            return false;
        }
    }

    public int deleteAll(int userId) {
        int count = 0;
        for (EvidenceItem item : getAll(userId)) {
            if (delete(userId, item)) count++;
        }
        return count;
    }
}
