package com.example.safesphere.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.safesphere.R;
import com.example.safesphere.data.User;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.home.MainActivity;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.settings.SafetyPreferences;
import com.example.safesphere.utils.AppExecutors;

/**
 * Entry point. Validates the stored session against the users table (a stale id is treated as
 * signed out), applies the optional biometric lock, then routes to Welcome, Profile Setup or Home.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long MIN_DISPLAY_MS = 500L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        findViewById(R.id.btnUnlock).setOnClickListener(v -> promptUnlock(currentUser));

        long start = System.currentTimeMillis();
        SessionManager session = new SessionManager(this);
        AppExecutors.io(() -> {
            User user = session.isLoggedIn()
                    ? new UserRepository(this).getUserById(session.getUserId())
                    : null;
            if (session.isLoggedIn() && user == null) session.clearSession();
            long wait = Math.max(0, MIN_DISPLAY_MS - (System.currentTimeMillis() - start));
            AppExecutors.mainDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                route(user);
            }, wait);
        });
    }

    private User currentUser;

    private void route(User user) {
        currentUser = user;
        if (user == null) {
            open(new Intent(this, WelcomeActivity.class));
        } else if (new SafetyPreferences(this).isBiometricLockEnabled() && canUseBiometrics()) {
            promptUnlock(user);
        } else {
            openForUser(user);
        }
    }

    private boolean canUseBiometrics() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void promptUnlock(User user) {
        if (user == null) return;
        findViewById(R.id.lockGroup).setVisibility(View.GONE);
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        openForUser(user);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        showLocked(errString);
                    }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_prompt_title))
                .setSubtitle(getString(R.string.lock_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.common_cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build());
    }

    private void showLocked(CharSequence reason) {
        findViewById(R.id.lockGroup).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvLockReason)).setText(reason);
    }

    private void openForUser(User user) {
        Intent next = user.profileCompleted
                ? new Intent(this, MainActivity.class)
                : new Intent(this, ProfileSetupActivity.class);
        open(next);
    }

    private void open(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
