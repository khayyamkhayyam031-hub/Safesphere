package com.example.safesphere.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.data.User;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.home.MainActivity;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.PasswordHasher;
import com.example.safesphere.utils.UiUtils;
import com.example.safesphere.utils.ValidationUtils;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilPhone;
    private TextInputLayout tilPassword;
    private Button btnLogin;
    private View pbLoading;
    private View errorBanner;
    private boolean isLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        UiUtils.setupToolbar(this, R.string.empty);

        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        pbLoading = findViewById(R.id.pbLoading);
        errorBanner = findViewById(R.id.errorBanner);
        AuthForm.clearErrorOnEdit(tilPhone, tilPassword);

        btnLogin.setOnClickListener(v -> attemptLogin());
        tilPassword.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        findViewById(R.id.tvCreateAccount).setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        if (isLoading) return;
        AuthForm.showBanner(errorBanner, null);

        String phone = ValidationUtils.normalizePhone(AuthForm.text(tilPhone));
        char[] password = ValidationUtils.toChars(tilPassword.getEditText().getText());

        boolean valid = true;
        if (AuthForm.text(tilPhone).trim().isEmpty()) {
            tilPhone.setError(getString(R.string.error_phone_required));
            valid = false;
        } else if (phone == null) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            valid = false;
        }
        if (password.length == 0) {
            tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        }
        if (!valid) {
            PasswordHasher.wipe(password);
            return;
        }

        setLoading(true);
        UserRepository repository = new UserRepository(this);
        AppExecutors.io(() -> {
            User user = repository.verifyLogin(phone, password);
            PasswordHasher.wipe(password);
            AppExecutors.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                if (user == null) {
                    // One message for every failure, so it never reveals which numbers exist.
                    AuthForm.showBanner(errorBanner, getString(R.string.login_error_invalid_credentials));
                    return;
                }
                new SessionManager(this).startSession(user.id);
                Intent next = new Intent(this, user.profileCompleted ? MainActivity.class : ProfileSetupActivity.class);
                next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(next);
                finish();
            });
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "" : getString(R.string.login_button));
        btnLogin.setContentDescription(loading ? getString(R.string.login_loading) : null);
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        tilPhone.setEnabled(!loading);
        tilPassword.setEnabled(!loading);
    }
}
