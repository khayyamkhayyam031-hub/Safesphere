package com.example.safesphere.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.PasswordHasher;
import com.example.safesphere.utils.UiUtils;
import com.example.safesphere.utils.ValidationUtils;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;

/** Registration → local session → Profile Setup → Home. */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilFullName;
    private TextInputLayout tilPhone;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmPassword;
    private CheckBox chkTerms;
    private Button btnRegister;
    private View pbLoading;
    private View errorBanner;
    private boolean isLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        UiUtils.setupToolbar(this, R.string.empty);

        tilFullName = findViewById(R.id.tilFullName);
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        chkTerms = findViewById(R.id.chkTerms);
        btnRegister = findViewById(R.id.btnRegister);
        pbLoading = findViewById(R.id.pbLoading);
        errorBanner = findViewById(R.id.errorBanner);
        AuthForm.clearErrorOnEdit(tilFullName, tilPhone, tilPassword, tilConfirmPassword);

        btnRegister.setOnClickListener(v -> attemptRegister());
        chkTerms.setOnCheckedChangeListener((b, checked) -> {
            if (checked) AuthForm.showBanner(errorBanner, null);
        });
        findViewById(R.id.btnReadTerms).setOnClickListener(v -> UiUtils.showTerms(this));
        findViewById(R.id.btnReadPrivacy).setOnClickListener(v -> UiUtils.showPrivacyPolicy(this));
        findViewById(R.id.tvLoginLink).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        if (isLoading) return;
        AuthForm.showBanner(errorBanner, null);

        String fullName = AuthForm.text(tilFullName).trim();
        String rawPhone = AuthForm.text(tilPhone).trim();
        String phone = ValidationUtils.normalizePhone(rawPhone);
        char[] password = ValidationUtils.toChars(tilPassword.getEditText().getText());
        char[] confirm = ValidationUtils.toChars(tilConfirmPassword.getEditText().getText());

        View firstInvalid = null;
        if (fullName.isEmpty()) {
            tilFullName.setError(getString(R.string.error_name_required));
            firstInvalid = tilFullName;
        } else if (!ValidationUtils.isValidName(fullName)) {
            tilFullName.setError(getString(R.string.error_name_invalid));
            firstInvalid = tilFullName;
        }
        if (rawPhone.isEmpty()) {
            tilPhone.setError(getString(R.string.error_phone_required));
            if (firstInvalid == null) firstInvalid = tilPhone;
        } else if (phone == null) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            if (firstInvalid == null) firstInvalid = tilPhone;
        }
        if (password.length == 0) {
            tilPassword.setError(getString(R.string.error_password_required));
            if (firstInvalid == null) firstInvalid = tilPassword;
        } else if (!ValidationUtils.isValidPassword(tilPassword.getEditText().getText())) {
            tilPassword.setError(getString(R.string.error_password_weak));
            if (firstInvalid == null) firstInvalid = tilPassword;
        }
        if (confirm.length == 0) {
            tilConfirmPassword.setError(getString(R.string.error_confirm_required));
            if (firstInvalid == null) firstInvalid = tilConfirmPassword;
        } else if (!Arrays.equals(password, confirm)) {
            tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
            if (firstInvalid == null) firstInvalid = tilConfirmPassword;
        }
        PasswordHasher.wipe(confirm);
        if (firstInvalid == null && !chkTerms.isChecked()) {
            AuthForm.showBanner(errorBanner, getString(R.string.error_terms_required));
            firstInvalid = chkTerms;
        }
        if (firstInvalid != null) {
            PasswordHasher.wipe(password);
            firstInvalid.requestFocus();
            return;
        }

        setLoading(true);
        UserRepository repository = new UserRepository(this);
        AppExecutors.io(() -> {
            UserRepository.RegisterResult result = repository.registerUser(fullName, phone, password);
            PasswordHasher.wipe(password);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                switch (result.status) {
                    case SUCCESS:
                        new SessionManager(this).startSession(result.userId);
                        Intent next = new Intent(this, ProfileSetupActivity.class);
                        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(next);
                        finish();
                        break;
                    case DUPLICATE_PHONE:
                        tilPhone.setError(getString(R.string.error_phone_exists));
                        tilPhone.requestFocus();
                        break;
                    default:
                        AuthForm.showBanner(errorBanner, getString(R.string.error_save_failed));
                        break;
                }
            });
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "" : getString(R.string.register_create_account));
        btnRegister.setContentDescription(loading ? getString(R.string.register_loading) : null);
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        tilFullName.setEnabled(!loading);
        tilPhone.setEnabled(!loading);
        tilPassword.setEnabled(!loading);
        tilConfirmPassword.setEnabled(!loading);
        chkTerms.setEnabled(!loading);
    }
}
