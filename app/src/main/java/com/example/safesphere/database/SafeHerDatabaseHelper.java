package com.example.safesphere.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Schema owner for the local {@code safeher.db} database, which lives in private app storage.
 * Queries live in the repositories; this class only creates and migrates tables.
 */
public class SafeHerDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "safeher.db";
    private static final int DATABASE_VERSION = 2;

    // users
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FULL_NAME = "full_name";
    public static final String COLUMN_PHONE_NUMBER = "phone_number";
    public static final String COLUMN_PASSWORD_HASH = "password_hash";
    public static final String COLUMN_PASSWORD_SALT = "password_salt";
    public static final String COLUMN_PROFILE_PHOTO_PATH = "profile_photo_path";
    public static final String COLUMN_PROFILE_COMPLETED = "profile_completed";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";

    // user_profiles
    public static final String TABLE_USER_PROFILES = "user_profiles";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_BLOOD_GROUP = "blood_group";
    public static final String COLUMN_MEDICAL_NOTES = "medical_notes";

    // guardians
    public static final String TABLE_GUARDIANS = "guardians";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_RELATIONSHIP = "relationship";
    public static final String COLUMN_TIER = "tier";
    public static final String COLUMN_INVITE_STATUS = "invite_status";

    // events
    public static final String TABLE_EVENTS = "events";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_TRIGGER = "trigger_type";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_STARTED_AT = "started_at";
    public static final String COLUMN_ENDED_AT = "ended_at";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_ACCURACY = "accuracy_m";
    public static final String COLUMN_LOCATION_TIME = "location_time";
    public static final String COLUMN_LOCATION_STATUS = "location_status";
    public static final String COLUMN_ALERT_STATUS = "alert_status";
    public static final String COLUMN_EVIDENCE_STATUS = "evidence_status";
    public static final String COLUMN_DESTINATION = "destination";
    public static final String COLUMN_EXPECTED_ARRIVAL = "expected_arrival";
    public static final String COLUMN_NOTES = "notes";

    // alert_attempts
    public static final String TABLE_ALERT_ATTEMPTS = "alert_attempts";
    public static final String COLUMN_EVENT_ID = "event_id";
    public static final String COLUMN_GUARDIAN_NAME = "guardian_name";
    public static final String COLUMN_ERROR = "error";

    // evidence
    public static final String TABLE_EVIDENCE = "evidence";
    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_DURATION_MS = "duration_ms";

    private static volatile SafeHerDatabaseHelper instance;

    /** One shared helper so every repository uses the same connection. */
    public static SafeHerDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (SafeHerDatabaseHelper.class) {
                if (instance == null) {
                    instance = new SafeHerDatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SafeHerDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USERS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_FULL_NAME + " TEXT NOT NULL,"
                + COLUMN_PHONE_NUMBER + " TEXT NOT NULL UNIQUE,"
                + COLUMN_PASSWORD_HASH + " TEXT NOT NULL,"
                + COLUMN_PASSWORD_SALT + " TEXT NOT NULL,"
                + COLUMN_PROFILE_PHOTO_PATH + " TEXT,"
                + COLUMN_PROFILE_COMPLETED + " INTEGER NOT NULL DEFAULT 0,"
                + COLUMN_CREATED_AT + " TEXT NOT NULL,"
                + COLUMN_UPDATED_AT + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_USER_PROFILES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USER_ID + " INTEGER NOT NULL UNIQUE,"
                + COLUMN_BLOOD_GROUP + " TEXT,"
                + COLUMN_MEDICAL_NOTES + " TEXT,"
                + COLUMN_CREATED_AT + " TEXT NOT NULL,"
                + COLUMN_UPDATED_AT + " TEXT NOT NULL,"
                + " FOREIGN KEY (" + COLUMN_USER_ID + ") REFERENCES "
                + TABLE_USERS + " (" + COLUMN_ID + ") ON DELETE CASCADE)");

        createVersion2Tables(db);
    }

    private void createVersion2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_GUARDIANS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USER_ID + " INTEGER NOT NULL,"
                + COLUMN_NAME + " TEXT NOT NULL,"
                + COLUMN_PHONE_NUMBER + " TEXT NOT NULL,"
                + COLUMN_RELATIONSHIP + " TEXT,"
                + COLUMN_TIER + " INTEGER NOT NULL DEFAULT 1,"
                + COLUMN_INVITE_STATUS + " TEXT NOT NULL,"
                + COLUMN_CREATED_AT + " INTEGER NOT NULL,"
                + COLUMN_UPDATED_AT + " INTEGER NOT NULL,"
                + " UNIQUE (" + COLUMN_USER_ID + ", " + COLUMN_PHONE_NUMBER + "),"
                + " FOREIGN KEY (" + COLUMN_USER_ID + ") REFERENCES "
                + TABLE_USERS + " (" + COLUMN_ID + ") ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EVENTS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USER_ID + " INTEGER NOT NULL,"
                + COLUMN_TYPE + " TEXT NOT NULL,"
                + COLUMN_TRIGGER + " TEXT,"
                + COLUMN_STATUS + " TEXT NOT NULL,"
                + COLUMN_STARTED_AT + " INTEGER NOT NULL,"
                + COLUMN_ENDED_AT + " INTEGER NOT NULL DEFAULT 0,"
                + COLUMN_LATITUDE + " REAL,"
                + COLUMN_LONGITUDE + " REAL,"
                + COLUMN_ACCURACY + " REAL,"
                + COLUMN_LOCATION_TIME + " INTEGER NOT NULL DEFAULT 0,"
                + COLUMN_LOCATION_STATUS + " TEXT NOT NULL,"
                + COLUMN_ALERT_STATUS + " TEXT NOT NULL,"
                + COLUMN_EVIDENCE_STATUS + " TEXT NOT NULL,"
                + COLUMN_DESTINATION + " TEXT,"
                + COLUMN_EXPECTED_ARRIVAL + " INTEGER NOT NULL DEFAULT 0,"
                + COLUMN_NOTES + " TEXT,"
                + " FOREIGN KEY (" + COLUMN_USER_ID + ") REFERENCES "
                + TABLE_USERS + " (" + COLUMN_ID + ") ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_user_started ON "
                + TABLE_EVENTS + " (" + COLUMN_USER_ID + ", " + COLUMN_STARTED_AT + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ALERT_ATTEMPTS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_EVENT_ID + " INTEGER NOT NULL,"
                + COLUMN_GUARDIAN_NAME + " TEXT NOT NULL,"
                + COLUMN_PHONE_NUMBER + " TEXT NOT NULL,"
                + COLUMN_STATUS + " TEXT NOT NULL,"
                + COLUMN_ERROR + " TEXT,"
                + COLUMN_UPDATED_AT + " INTEGER NOT NULL,"
                + " FOREIGN KEY (" + COLUMN_EVENT_ID + ") REFERENCES "
                + TABLE_EVENTS + " (" + COLUMN_ID + ") ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EVIDENCE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USER_ID + " INTEGER NOT NULL,"
                + COLUMN_EVENT_ID + " INTEGER,"
                + COLUMN_FILE_PATH + " TEXT NOT NULL,"
                + COLUMN_DURATION_MS + " INTEGER NOT NULL DEFAULT 0,"
                + COLUMN_STATUS + " TEXT NOT NULL,"
                + COLUMN_CREATED_AT + " INTEGER NOT NULL,"
                + " FOREIGN KEY (" + COLUMN_USER_ID + ") REFERENCES "
                + TABLE_USERS + " (" + COLUMN_ID + ") ON DELETE CASCADE,"
                + " FOREIGN KEY (" + COLUMN_EVENT_ID + ") REFERENCES "
                + TABLE_EVENTS + " (" + COLUMN_ID + ") ON DELETE SET NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 only had users and user_profiles; keep them and add the new tables.
        if (oldVersion < 2) {
            createVersion2Tables(db);
        }
    }
}
