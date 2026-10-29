package com.example.safesphere.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.safesphere.R;
import com.example.safesphere.auth.ProfileSetupActivity;
import com.example.safesphere.auth.WelcomeActivity;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.database.EvidenceRepository;
import com.example.safesphere.database.UserRepository;
import com.example.safesphere.evidence.EvidenceActivity;
import com.example.safesphere.location.LocationHelper;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.PasswordHasher;
import com.example.safesphere.utils.UiUtils;
import com.example.safesphere.utils.ValidationUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;

/** Settings. Only implemented features get a switch; the rest say plainly that they are unavailable. */
public class SettingsFragment extends Fragment {

    private View root;
    private SafetyPreferences prefs;
    private int userId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = new SafetyPreferences(requireContext());
        userId = new SessionManager(requireContext()).getUserId();

        // Account
        row(R.id.rowProfile, R.drawable.ic_person, R.string.settings_profile, R.string.settings_profile_sub,
                v -> startActivity(new Intent(requireContext(), ProfileSetupActivity.class)
                        .putExtra(ProfileSetupActivity.EXTRA_EDIT, true)));
        row(R.id.rowPassword, R.drawable.ic_lock, R.string.settings_change_password, 0, v -> changePassword());
        row(R.id.rowLogout, R.drawable.ic_logout, R.string.settings_logout, 0, v -> confirmLogout());
        row(R.id.rowDeleteAccount, R.drawable.ic_delete, R.string.settings_delete_account,
                R.string.settings_delete_account_sub, v -> confirmDeleteAccount());
        ((TextView) root.findViewById(R.id.rowDeleteAccount).findViewById(R.id.tvTitle))
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.emergency_red_text));

        // Safety
        row(R.id.rowCountdown, R.drawable.ic_timer, R.string.settings_sos_cancellation,
                R.string.settings_sos_cancellation_sub, v -> chooseCountdown());
        row(R.id.rowShake, R.drawable.ic_refresh, R.string.settings_shake_sos, R.string.settings_shake_sub, v -> {
            prefs.setShakeSosEnabled(!prefs.isShakeSosEnabled());
            bindValues();
        });
        row(R.id.rowVoice, R.drawable.ic_mic, R.string.settings_voice_trigger, R.string.settings_not_in_version, null);
        row(R.id.rowPower, R.drawable.ic_warning, R.string.settings_power_button, R.string.settings_not_supported, null);
        row(R.id.rowPermissions, R.drawable.ic_shield_small, R.string.settings_permissions,
                R.string.settings_permissions_sub, v -> UiUtils.openAppSettings(requireContext()));

        // Privacy
        row(R.id.rowLocationPrefs, R.drawable.ic_location, R.string.settings_location_prefs, 0,
                v -> UiUtils.openAppSettings(requireContext()));
        row(R.id.rowEvidencePrefs, R.drawable.ic_mic, R.string.settings_evidence_prefs, R.string.settings_evidence_sub,
                v -> startActivity(new Intent(requireContext(), EvidenceActivity.class)));
        row(R.id.rowClearHistory, R.drawable.ic_history, R.string.settings_clear_history,
                R.string.settings_clear_history_sub, v -> confirmClearHistory());

        // Security
        row(R.id.rowBiometric, R.drawable.ic_lock, R.string.settings_biometric_lock, 0, v -> toggleBiometric());
        row(R.id.rowSession, R.drawable.ic_person, R.string.settings_session, R.string.settings_session_sub, null);

        // About
        row(R.id.rowVersion, R.drawable.ic_info, R.string.settings_version, 0, null);
        row(R.id.rowPrivacyPolicy, R.drawable.ic_shield_small, R.string.settings_privacy_policy, 0,
                v -> UiUtils.showPrivacyPolicy(requireContext()));
        row(R.id.rowTerms, R.drawable.ic_info, R.string.settings_terms, 0, v -> UiUtils.showTerms(requireContext()));
        row(R.id.rowHelp, R.drawable.ic_helpline, R.string.settings_help, 0,
                v -> UiUtils.showInfo(requireContext(), R.string.help_title, R.string.help_body));

        setValue(R.id.rowVersion, versionName());
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) bindValues();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) bindValues();
    }

    private void bindValues() {
        if (root == null) return;
        setValue(R.id.rowCountdown, getString(R.string.settings_seconds, prefs.getCountdownSeconds()));
        setSwitch(R.id.rowShake, prefs.isShakeSosEnabled());
        setValue(R.id.rowVoice, getString(R.string.status_unavailable));
        setValue(R.id.rowPower, getString(R.string.status_unavailable));

        Context c = requireContext();
        setSubtitle(R.id.rowLocationPrefs, !LocationHelper.hasPermission(c)
                ? getString(R.string.settings_location_off)
                : LocationHelper.isLocationEnabled(c) ? getString(R.string.settings_location_on)
                : getString(R.string.status_location_disabled));

        boolean biometricAvailable = biometricStatus() == BiometricManager.BIOMETRIC_SUCCESS;
        boolean lockOn = prefs.isBiometricLockEnabled() && biometricAvailable;
        setSwitch(R.id.rowBiometric, lockOn);
        setSubtitle(R.id.rowBiometric, getString(biometricAvailable
                ? R.string.settings_biometric_sub : R.string.settings_biometric_unavailable));
    }

    // ---------- Row helpers ----------

    private void row(int id, @DrawableRes int icon, int title, int subtitle, @Nullable View.OnClickListener l) {
        View row = root.findViewById(id);
        ((ImageView) row.findViewById(R.id.ivIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.tvTitle)).setText(title);
        if (subtitle != 0) setSubtitle(id, getString(subtitle));
        if (l != null) {
            row.setOnClickListener(l);
        } else {
            row.setClickable(false);
            row.setFocusable(false);
            row.setBackground(null);
        }
    }

    private void setSubtitle(int id, String text) {
        TextView sub = root.findViewById(id).findViewById(R.id.tvSubtitle);
        sub.setText(text);
        sub.setVisibility(View.VISIBLE);
    }

    private void setValue(int id, String value) {
        TextView tv = root.findViewById(id).findViewById(R.id.tvValue);
        tv.setText(value);
        tv.setVisibility(View.VISIBLE);
    }

    private void setSwitch(int id, boolean on) {
        View row = root.findViewById(id);
        MaterialSwitch sw = row.findViewById(R.id.swToggle);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(on);
        // The row is the control; announce it as a switch with its state.
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(sw.isChecked());
            }
        });
    }

    private String versionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.common_not_available);
        }
    }

    private void snack(String message) {
        if (getView() != null) Snackbar.make(getView(), message, Snackbar.LENGTH_LONG).show();
    }

    // ---------- Safety ----------

    private void chooseCountdown() {
        int[] options = SafetyPreferences.COUNTDOWN_OPTIONS;
        String[] labels = new String[options.length];
        int checked = 0;
        for (int i = 0; i < options.length; i++) {
            labels[i] = getString(R.string.settings_seconds, options[i]);
            if (options[i] == prefs.getCountdownSeconds()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_sos_cancellation)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.setCountdownSeconds(options[which]);
                    bindValues();
                    d.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    // ---------- Security ----------

    private int biometricStatus() {
        return BiometricManager.from(requireContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
    }

    private void toggleBiometric() {
        if (prefs.isBiometricLockEnabled()) {
            prefs.setBiometricLockEnabled(false);
            bindValues();
            return;
        }
        if (biometricStatus() != BiometricManager.BIOMETRIC_SUCCESS) {
            UiUtils.showInfo(requireContext(), R.string.settings_biometric_lock, R.string.settings_biometric_unavailable_long);
            return;
        }
        // Turn it on only after one successful check, so the user cannot lock themselves out.
        new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        prefs.setBiometricLockEnabled(true);
                        bindValues();
                        snack(getString(R.string.settings_biometric_enabled));
                    }
                }).authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.settings_biometric_confirm_title))
                .setNegativeButtonText(getString(R.string.common_cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build());
    }

    // ---------- Account ----------

    private void changePassword() {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
        TextInputLayout tilCurrent = form.findViewById(R.id.tilCurrent);
        TextInputLayout tilNew = form.findViewById(R.id.tilNew);
        TextInputLayout tilConfirm = form.findViewById(R.id.tilConfirm);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_change_password)
                .setView(form)
                .setPositiveButton(R.string.common_save, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilCurrent.setError(null);
            tilNew.setError(null);
            tilConfirm.setError(null);
            char[] current = ValidationUtils.toChars(tilCurrent.getEditText().getText());
            char[] next = ValidationUtils.toChars(tilNew.getEditText().getText());
            char[] confirm = ValidationUtils.toChars(tilConfirm.getEditText().getText());
            boolean ok = true;
            if (current.length == 0) {
                tilCurrent.setError(getString(R.string.error_password_required));
                ok = false;
            }
            if (!ValidationUtils.isValidPassword(tilNew.getEditText().getText())) {
                tilNew.setError(getString(R.string.error_password_weak));
                ok = false;
            } else if (!Arrays.equals(next, confirm)) {
                tilConfirm.setError(getString(R.string.error_password_mismatch));
                ok = false;
            }
            PasswordHasher.wipe(confirm);
            if (!ok) {
                PasswordHasher.wipe(current);
                PasswordHasher.wipe(next);
                return;
            }
            v.setEnabled(false);
            Context app = requireContext().getApplicationContext();
            AppExecutors.io(() -> {
                UserRepository.PasswordChangeResult result = new UserRepository(app).updatePassword(userId, current, next);
                PasswordHasher.wipe(current);
                PasswordHasher.wipe(next);
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    v.setEnabled(true);
                    if (result == UserRepository.PasswordChangeResult.SUCCESS) {
                        dialog.dismiss();
                        snack(getString(R.string.settings_password_changed));
                    } else if (result == UserRepository.PasswordChangeResult.WRONG_CURRENT_PASSWORD) {
                        tilCurrent.setError(getString(R.string.settings_wrong_password));
                    } else {
                        tilCurrent.setError(getString(R.string.error_save_failed));
                    }
                });
            });
        }));
        dialog.show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_logout)
                .setMessage(R.string.settings_logout_message)
                .setPositiveButton(R.string.settings_logout, (d, w) -> {
                    new SessionManager(requireContext()).clearSession();
                    goToWelcome();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void confirmDeleteAccount() {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_password, null);
        TextInputLayout til = form.findViewById(R.id.tilPassword);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_delete_account)
                .setMessage(R.string.settings_delete_account_message)
                .setView(form)
                .setPositiveButton(R.string.common_delete, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            char[] password = ValidationUtils.toChars(til.getEditText().getText());
            if (password.length == 0) {
                til.setError(getString(R.string.error_password_required));
                return;
            }
            v.setEnabled(false);
            Context app = requireContext().getApplicationContext();
            AppExecutors.io(() -> {
                UserRepository users = new UserRepository(app);
                boolean verified = users.verifyPassword(userId, password);
                PasswordHasher.wipe(password);
                boolean deleted = false;
                if (verified) {
                    new EvidenceRepository(app).deleteAll(userId); // removes the audio files
                    deleted = users.deleteUser(userId);
                }
                boolean finalDeleted = deleted;
                AppExecutors.main(() -> {
                    if (!isAdded()) return;
                    v.setEnabled(true);
                    if (!verified) {
                        til.setError(getString(R.string.settings_wrong_password));
                    } else if (!finalDeleted) {
                        til.setError(getString(R.string.error_save_failed));
                    } else {
                        dialog.dismiss();
                        new SessionManager(app).clearSession();
                        prefs.clear();
                        Toast.makeText(app, R.string.settings_account_deleted, Toast.LENGTH_LONG).show();
                        goToWelcome();
                    }
                });
            });
        }));
        dialog.show();
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.settings_clear_history_message)
                .setPositiveButton(R.string.common_delete, (d, w) -> {
                    Context app = requireContext().getApplicationContext();
                    AppExecutors.io(() -> {
                        int count = new EventRepository(app).deleteClosedHistory(userId);
                        AppExecutors.main(() -> {
                            if (isAdded()) snack(getResources().getQuantityString(R.plurals.settings_history_cleared, count, count));
                        });
                    });
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void goToWelcome() {
        Intent i = new Intent(requireContext(), WelcomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        root = null;
    }
}
