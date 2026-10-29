package com.example.safesphere.database;

import static com.example.safesphere.database.SafeHerDatabaseHelper.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.safesphere.data.User;
import com.example.safesphere.data.UserProfile;
import com.example.safesphere.utils.PasswordHasher;

import java.io.File;
import java.security.GeneralSecurityException;

/**
 * Local account storage. All methods hit SQLite or run PBKDF2 and must be called off the main
 * thread. Phone numbers passed in must already be normalised by
 * {@link com.example.safesphere.utils.ValidationUtils#normalizePhone(String)}.
 *
 * <p>This is device-local authentication: it protects the app on this phone but does not prove
 * the user's real-world identity or that they own the phone number.
 */
@WorkerThread
public class UserRepository {

    private static final String TAG = "UserRepository";

    public enum RegisterStatus { SUCCESS, DUPLICATE_PHONE, ERROR }

    public static final class RegisterResult {
        public final RegisterStatus status;
        public final int userId;

        RegisterResult(RegisterStatus status, int userId) {
            this.status = status;
            this.userId = userId;
        }
    }

    public enum PasswordChangeResult { SUCCESS, WRONG_CURRENT_PASSWORD, ERROR }

    private final SafeHerDatabaseHelper dbHelper;

    public UserRepository(Context context) {
        dbHelper = SafeHerDatabaseHelper.getInstance(context);
    }

    public RegisterResult registerUser(String fullName, String phoneNumber, char[] password) {
        if (isPhoneRegistered(phoneNumber)) {
            return new RegisterResult(RegisterStatus.DUPLICATE_PHONE, -1);
        }

        PasswordHasher.Hashed hashed;
        try {
            hashed = PasswordHasher.hash(password);
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Password hashing unavailable", e);
            return new RegisterResult(RegisterStatus.ERROR, -1);
        }

        String now = String.valueOf(System.currentTimeMillis());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues user = new ContentValues();
            user.put(COLUMN_FULL_NAME, fullName.trim());
            user.put(COLUMN_PHONE_NUMBER, phoneNumber);
            user.put(COLUMN_PASSWORD_HASH, hashed.hash);
            user.put(COLUMN_PASSWORD_SALT, hashed.salt);
            user.put(COLUMN_PROFILE_COMPLETED, 0);
            user.put(COLUMN_CREATED_AT, now);
            user.put(COLUMN_UPDATED_AT, now);
            long userId = db.insertOrThrow(TABLE_USERS, null, user);

            ContentValues profile = new ContentValues();
            profile.put(COLUMN_USER_ID, userId);
            profile.put(COLUMN_CREATED_AT, now);
            profile.put(COLUMN_UPDATED_AT, now);
            db.insertOrThrow(TABLE_USER_PROFILES, null, profile);

            db.setTransactionSuccessful();
            return new RegisterResult(RegisterStatus.SUCCESS, (int) userId);
        } catch (SQLiteConstraintException e) {
            // Lost a race with another registration for the same number.
            return new RegisterResult(RegisterStatus.DUPLICATE_PHONE, -1);
        } catch (SQLException e) {
            Log.e(TAG, "Registration insert failed", e);
            return new RegisterResult(RegisterStatus.ERROR, -1);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Returns the user on success, or null for any failure. Callers must show one generic
     * message so the result never reveals whether a number is registered.
     */
    @Nullable
    public User verifyLogin(String phoneNumber, char[] password) {
        String[] credentials = readCredentials(COLUMN_PHONE_NUMBER + " = ?", phoneNumber);
        if (credentials == null) {
            PasswordHasher.burnEquivalentWork(password);
            return null;
        }
        try {
            if (!PasswordHasher.verify(password, credentials[1], credentials[2])) return null;
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Password verification unavailable", e);
            return null;
        }
        return getUserById(Integer.parseInt(credentials[0]));
    }

    public boolean verifyPassword(int userId, char[] password) {
        String[] credentials = readCredentials(COLUMN_ID + " = ?", String.valueOf(userId));
        if (credentials == null) return false;
        try {
            return PasswordHasher.verify(password, credentials[1], credentials[2]);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** id, hash, salt — kept private to this class. */
    @Nullable
    private String[] readCredentials(String selection, String arg) {
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_USERS,
                new String[]{COLUMN_ID, COLUMN_PASSWORD_HASH, COLUMN_PASSWORD_SALT},
                selection, new String[]{arg}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            return new String[]{c.getString(0), c.getString(1), c.getString(2)};
        } catch (SQLException e) {
            Log.e(TAG, "Credential lookup failed", e);
            return null;
        }
    }

    public boolean isPhoneRegistered(String phoneNumber) {
        return getUserByPhone(phoneNumber) != null;
    }

    @Nullable
    public User getUserByPhone(String phoneNumber) {
        return queryUser(COLUMN_PHONE_NUMBER + " = ?", phoneNumber);
    }

    @Nullable
    public User getUserById(int userId) {
        return queryUser(COLUMN_ID + " = ?", String.valueOf(userId));
    }

    @Nullable
    private User queryUser(String selection, String arg) {
        String[] columns = {COLUMN_ID, COLUMN_FULL_NAME, COLUMN_PHONE_NUMBER,
                COLUMN_PROFILE_PHOTO_PATH, COLUMN_PROFILE_COMPLETED, COLUMN_CREATED_AT, COLUMN_UPDATED_AT};
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_USERS, columns, selection,
                new String[]{arg}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            User user = new User();
            user.id = c.getInt(0);
            user.fullName = c.getString(1);
            user.phoneNumber = c.getString(2);
            user.profilePhotoPath = c.isNull(3) ? null : c.getString(3);
            user.profileCompleted = c.getInt(4) == 1;
            user.createdAt = parseLong(c.getString(5));
            user.updatedAt = parseLong(c.getString(6));
            return user;
        } catch (SQLException e) {
            Log.e(TAG, "User lookup failed", e);
            return null;
        }
    }

    @Nullable
    public UserProfile getUserProfile(int userId) {
        try (Cursor c = dbHelper.getReadableDatabase().query(TABLE_USER_PROFILES,
                new String[]{COLUMN_ID, COLUMN_USER_ID, COLUMN_BLOOD_GROUP, COLUMN_MEDICAL_NOTES,
                        COLUMN_CREATED_AT, COLUMN_UPDATED_AT},
                COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            UserProfile p = new UserProfile();
            p.id = c.getInt(0);
            p.userId = c.getInt(1);
            p.bloodGroup = c.isNull(2) ? null : c.getString(2);
            p.medicalNotes = c.isNull(3) ? null : c.getString(3);
            p.createdAt = parseLong(c.getString(4));
            p.updatedAt = parseLong(c.getString(5));
            return p;
        } catch (SQLException e) {
            Log.e(TAG, "Profile lookup failed", e);
            return null;
        }
    }

    /**
     * Saves the profile and marks it complete. {@code profilePhotoPath} replaces the stored
     * photo only when non-null. Returns false if nothing was saved.
     */
    public boolean updateUserProfile(int userId, String fullName, @Nullable String bloodGroup,
                                     @Nullable String medicalNotes, @Nullable String profilePhotoPath) {
        String now = String.valueOf(System.currentTimeMillis());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues user = new ContentValues();
            user.put(COLUMN_FULL_NAME, fullName.trim());
            user.put(COLUMN_PROFILE_COMPLETED, 1);
            user.put(COLUMN_UPDATED_AT, now);
            if (profilePhotoPath != null) user.put(COLUMN_PROFILE_PHOTO_PATH, profilePhotoPath);
            int rows = db.update(TABLE_USERS, user, COLUMN_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            if (rows != 1) return false;

            ContentValues profile = new ContentValues();
            profile.put(COLUMN_BLOOD_GROUP, emptyToNull(bloodGroup));
            profile.put(COLUMN_MEDICAL_NOTES, emptyToNull(medicalNotes));
            profile.put(COLUMN_UPDATED_AT, now);
            int profileRows = db.update(TABLE_USER_PROFILES, profile, COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            if (profileRows == 0) {
                profile.put(COLUMN_USER_ID, userId);
                profile.put(COLUMN_CREATED_AT, now);
                db.insertOrThrow(TABLE_USER_PROFILES, null, profile);
            }
            db.setTransactionSuccessful();
            return true;
        } catch (SQLException e) {
            Log.e(TAG, "Profile update failed", e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public PasswordChangeResult updatePassword(int userId, char[] currentPassword, char[] newPassword) {
        if (!verifyPassword(userId, currentPassword)) return PasswordChangeResult.WRONG_CURRENT_PASSWORD;
        try {
            PasswordHasher.Hashed hashed = PasswordHasher.hash(newPassword);
            ContentValues values = new ContentValues();
            values.put(COLUMN_PASSWORD_HASH, hashed.hash);
            values.put(COLUMN_PASSWORD_SALT, hashed.salt);
            values.put(COLUMN_UPDATED_AT, String.valueOf(System.currentTimeMillis()));
            int rows = dbHelper.getWritableDatabase().update(TABLE_USERS, values,
                    COLUMN_ID + " = ?", new String[]{String.valueOf(userId)});
            return rows == 1 ? PasswordChangeResult.SUCCESS : PasswordChangeResult.ERROR;
        } catch (GeneralSecurityException | SQLException e) {
            Log.e(TAG, "Password change failed", e);
            return PasswordChangeResult.ERROR;
        }
    }

    /**
     * Deletes the account. Guardians, history and evidence metadata go with it through
     * ON DELETE CASCADE; the caller removes the files in private storage.
     */
    public boolean deleteUser(int userId) {
        try {
            User user = getUserById(userId);
            int rows = dbHelper.getWritableDatabase().delete(TABLE_USERS, COLUMN_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            if (rows == 1 && user != null && user.profilePhotoPath != null) {
                //noinspection ResultOfMethodCallIgnored
                new File(user.profilePhotoPath).delete();
            }
            return rows == 1;
        } catch (SQLException e) {
            Log.e(TAG, "Account deletion failed", e);
            return false;
        }
    }

    private static String emptyToNull(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException | NullPointerException e) {
            return 0L;
        }
    }
}
