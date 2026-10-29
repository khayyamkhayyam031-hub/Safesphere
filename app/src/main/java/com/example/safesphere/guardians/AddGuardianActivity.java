package com.example.safesphere.guardians;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.Guardian;
import com.example.safesphere.data.User;
import com.example.safesphere.database.GuardianRepository;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.UiUtils;
import com.example.safesphere.utils.ValidationUtils;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Add or edit a guardian, typed in or picked from contacts.
 */
public class AddGuardianActivity extends AppCompatActivity {

    private static final String EXTRA_GUARDIAN_ID = "guardian_id";

    private TextInputLayout tilName;
    private TextInputLayout tilPhone;
    private MaterialAutoCompleteTextView etRelationship;
    private MaterialButtonToggleGroup tierGroup;
    private Button btnSave;
    private View errorBanner;

    private int userId;
    private long guardianId;
    private boolean saving;

    // Picking a phone row grants read access to just that row: no contacts permission needed.
    private final ActivityResultLauncher<Intent> contactPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> onContactPicked(result.getData() != null ? result.getData().getData() : null));

    public static Intent intent(Context context, long guardianId) {
        return new Intent(context, AddGuardianActivity.class).putExtra(EXTRA_GUARDIAN_ID, guardianId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_guardian);
        guardianId = getIntent().getLongExtra(EXTRA_GUARDIAN_ID, 0);
        UiUtils.setupToolbar(this, guardianId == 0 ? R.string.guardians_add_title : R.string.guardians_edit_title);
        userId = new SessionManager(this).getUserId();

        tilName = findViewById(R.id.tilName);
        tilPhone = findViewById(R.id.tilPhone);
        etRelationship = findViewById(R.id.etRelationship);
        tierGroup = findViewById(R.id.tierGroup);
        btnSave = findViewById(R.id.btnSave);
        errorBanner = findViewById(R.id.errorBanner);

        etRelationship.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.relationships)));

        findViewById(R.id.btnPickContact).setOnClickListener(v -> {
            try {
                contactPicker.launch(new Intent(Intent.ACTION_PICK)
                        .setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE));
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, R.string.common_no_app, Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());

        if (guardianId != 0 && savedInstanceState == null) loadExisting();
    }

    private void loadExisting() {
        AppExecutors.io(() -> {
            Guardian g = new GuardianRepository(this).getGuardian(userId, guardianId);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (g == null) {
                    finish();
                    return;
                }
                tilName.getEditText().setText(g.name);
                tilPhone.getEditText().setText(g.phoneNumber);
                if (g.relationship != null) etRelationship.setText(g.relationship, false);
                tierGroup.check(g.tier == Guardian.TIER_SECONDARY ? R.id.btnTier2 : R.id.btnTier1);
            });
        });
    }

    private void onContactPicked(@Nullable Uri contactUri) {
        if (contactUri == null) return;
        AppExecutors.io(() -> {
            String name = null;
            String phone = null;
            try (Cursor c = getContentResolver().query(contactUri,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    name = c.getString(0);
                    phone = c.getString(1);
                }
            } catch (SecurityException | IllegalArgumentException e) {
                // Picker did not grant the row; the user can type the details instead.
            }
            String finalName = name;
            String finalPhone = phone;
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalName != null) tilName.getEditText().setText(finalName);
                if (finalPhone != null) {
                    tilPhone.getEditText().setText(finalPhone);
                } else {
                    tilPhone.setError(getString(R.string.guardians_contact_no_number));
                }
            });
        });
    }

    private void save() {
        if (saving) return;
        errorBanner.setVisibility(View.GONE);
        tilName.setError(null);
        tilPhone.setError(null);

        String name = text(tilName).trim();
        String rawPhone = text(tilPhone).trim();
        String phone = ValidationUtils.normalizePhone(rawPhone);
        String relationship = etRelationship.getText().toString().trim();

        boolean valid = true;
        if (!ValidationUtils.isValidName(name)) {
            tilName.setError(getString(name.isEmpty() ? R.string.error_name_required : R.string.error_name_invalid));
            valid = false;
        }
        if (rawPhone.isEmpty()) {
            tilPhone.setError(getString(R.string.error_phone_required));
            valid = false;
        } else if (phone == null) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            valid = false;
        }
        if (!valid) return;

        Guardian g = new Guardian();
        g.id = guardianId;
        g.userId = userId;
        g.name = name;
        g.phoneNumber = phone;
        g.relationship = relationship.isEmpty() ? null : relationship;
        g.tier = tierGroup.getCheckedButtonId() == R.id.btnTier2 ? Guardian.TIER_SECONDARY : Guardian.TIER_PRIMARY;

        saving = true;
        btnSave.setEnabled(false);
        AppExecutors.io(() -> {
            User me = new UserRepository(this).getUserById(userId);
            boolean isSelf = me != null && phone.equals(me.phoneNumber);
            GuardianRepository.SaveResult result = isSelf ? null : new GuardianRepository(this).save(g);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                saving = false;
                btnSave.setEnabled(true);
                if (isSelf) {
                    tilPhone.setError(getString(R.string.guardians_error_self));
                } else if (result == GuardianRepository.SaveResult.SUCCESS) {
                    Toast.makeText(this, guardianId == 0
                            ? getString(R.string.guardians_added, name)
                            : getString(R.string.guardians_updated, name), Toast.LENGTH_SHORT).show();
                    finish();
                } else if (result == GuardianRepository.SaveResult.DUPLICATE) {
                    tilPhone.setError(getString(R.string.guardians_error_duplicate));
                } else {
                    errorBanner.setVisibility(View.VISIBLE);
                    ((android.widget.TextView) errorBanner.findViewById(R.id.tvError)).setText(R.string.guardians_error_save);
                }
            });
        });
    }

    private static String text(TextInputLayout til) {
        return til.getEditText() == null || til.getEditText().getText() == null ? "" : til.getEditText().getText().toString();
    }
}
