package com.example.safesphere.auth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.User;
import com.example.safesphere.data.UserProfile;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.home.MainActivity;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.UiUtils;
import com.example.safesphere.utils.ValidationUtils;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * First-run profile setup, also reused from Settings to edit the profile ({@link #EXTRA_EDIT}).
 * Only the name is required. The chosen photo is re-encoded into private storage, which also
 * strips its EXIF metadata (including any GPS tags).
 */
public class ProfileSetupActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT = "edit_mode";
    private static final String TAG = "ProfileSetup";
    private static final String STATE_PENDING_PHOTO = "pending_photo";
    private static final int MAX_PHOTO_PX = 512;

    private TextInputLayout tilFullName;
    private MaterialAutoCompleteTextView etBloodGroup;
    private TextInputLayout tilMedicalNotes;
    private ImageView ivPhoto;
    private TextView tvInitial;
    private Button btnSave;
    private View pbLoading;
    private View errorBanner;

    private boolean editMode;
    private int userId;
    private User user;
    /** New photo saved to private storage but not yet committed to the profile. */
    @Nullable
    private String pendingPhotoPath;
    private boolean isSaving;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        editMode = getIntent().getBooleanExtra(EXTRA_EDIT, false);
        UiUtils.setupToolbar(this, editMode ? R.string.profile_edit_title : R.string.empty);
        if (!editMode) {
            // First run: there is nowhere to go back to.
            ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar)).setNavigationIcon(null);
        } else {
            ((TextView) findViewById(R.id.tvHeading)).setText(R.string.profile_edit_heading);
        }

        tilFullName = findViewById(R.id.tilFullName);
        etBloodGroup = findViewById(R.id.etBloodGroup);
        tilMedicalNotes = findViewById(R.id.tilMedicalNotes);
        ivPhoto = findViewById(R.id.ivProfilePhoto);
        tvInitial = findViewById(R.id.tvAvatarInitial);
        btnSave = findViewById(R.id.btnSave);
        pbLoading = findViewById(R.id.pbLoading);
        errorBanner = findViewById(R.id.errorBanner);
        AuthForm.clearErrorOnEdit(tilFullName);

        etBloodGroup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.blood_groups)));

        View.OnClickListener pick = v -> photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
        findViewById(R.id.avatarFrame).setOnClickListener(pick);
        findViewById(R.id.btnChangePhoto).setOnClickListener(pick);
        btnSave.setOnClickListener(v -> save());

        if (savedInstanceState != null) pendingPhotoPath = savedInstanceState.getString(STATE_PENDING_PHOTO);

        userId = new SessionManager(this).getUserId();
        UserRepository repository = new UserRepository(this);
        AppExecutors.io(() -> {
            User u = repository.getUserById(userId);
            UserProfile p = repository.getUserProfile(userId);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (u == null) {
                    // Session points at a deleted account.
                    new SessionManager(this).clearSession();
                    Intent i = new Intent(this, WelcomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                    return;
                }
                bind(u, p, savedInstanceState == null);
            });
        });
    }

    private void bind(User u, @Nullable UserProfile p, boolean fillFields) {
        user = u;
        ((TextView) findViewById(R.id.etPhone)).setText(u.phoneNumber);
        if (fillFields) {
            tilFullName.getEditText().setText(u.fullName);
            if (p != null) {
                if (p.bloodGroup != null) etBloodGroup.setText(p.bloodGroup, false);
                if (p.medicalNotes != null) tilMedicalNotes.getEditText().setText(p.medicalNotes);
            }
        }
        refreshAvatar();
    }

    private void refreshAvatar() {
        String path = pendingPhotoPath != null ? pendingPhotoPath : (user != null ? user.profilePhotoPath : null);
        String name = AuthForm.text(tilFullName).trim();
        String initial = name.isEmpty() ? (user != null ? user.getInitial() : "?") : name.substring(0, 1).toUpperCase();
        UiUtils.bindAvatar(ivPhoto, tvInitial, path, initial);
    }

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null) return;
        AppExecutors.io(() -> {
            String path = copyPhoto(uri);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (path == null) {
                    AuthForm.showBanner(errorBanner, getString(R.string.profile_error_photo));
                    return;
                }
                pendingPhotoPath = path;
                AuthForm.showBanner(errorBanner, null);
                refreshAvatar();
            });
        });
    }

    @Nullable
    private String copyPhoto(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= MAX_PHOTO_PX && bounds.outHeight / (sample * 2) >= MAX_PHOTO_PX) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bitmap == null) return null;

            File dir = new File(getFilesDir(), "profile");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File out = new File(dir, "avatar_" + userId + "_" + System.currentTimeMillis() + ".jpg");
            try (OutputStream os = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, os);
            }
            bitmap.recycle();
            return out.getAbsolutePath();
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "Could not import photo", e);
            return null;
        }
    }

    private void save() {
        if (isSaving || user == null) return;
        AuthForm.showBanner(errorBanner, null);
        String name = AuthForm.text(tilFullName).trim();
        if (name.isEmpty()) {
            tilFullName.setError(getString(R.string.error_name_required));
            tilFullName.requestFocus();
            return;
        }
        if (!ValidationUtils.isValidName(name)) {
            tilFullName.setError(getString(R.string.error_name_invalid));
            tilFullName.requestFocus();
            return;
        }
        String blood = etBloodGroup.getText().toString().trim();
        if (blood.equals(getString(R.string.blood_group_unspecified))) blood = null;
        String notes = AuthForm.text(tilMedicalNotes).trim();
        String newPhoto = pendingPhotoPath;
        String oldPhoto = user.profilePhotoPath;

        setSaving(true);
        UserRepository repository = new UserRepository(this);
        String finalBlood = blood;
        AppExecutors.io(() -> {
            boolean ok = repository.updateUserProfile(userId, name, finalBlood, notes, newPhoto);
            if (ok && newPhoto != null && oldPhoto != null && !oldPhoto.equals(newPhoto)) {
                //noinspection ResultOfMethodCallIgnored
                new File(oldPhoto).delete();
            }
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                setSaving(false);
                if (!ok) {
                    // Keep everything the user typed so they can retry.
                    AuthForm.showBanner(errorBanner, getString(R.string.error_save_failed));
                    return;
                }
                pendingPhotoPath = null;
                if (editMode) {
                    android.widget.Toast.makeText(this, R.string.profile_saved, android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Intent home = new Intent(this, MainActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(home);
                    finish();
                }
            });
        });
    }

    private void setSaving(boolean saving) {
        isSaving = saving;
        btnSave.setEnabled(!saving);
        btnSave.setText(saving ? "" : getString(R.string.profile_setup_save));
        btnSave.setContentDescription(saving ? getString(R.string.common_saving) : null);
        pbLoading.setVisibility(saving ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_PHOTO, pendingPhotoPath);
    }
}
