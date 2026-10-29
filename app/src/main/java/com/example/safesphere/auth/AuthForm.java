package com.example.safesphere.auth;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

/** Field-error and banner helpers shared by the auth screens. */
final class AuthForm {

    private AuthForm() {
    }

    static String text(TextInputLayout til) {
        return til.getEditText() == null || til.getEditText().getText() == null
                ? "" : til.getEditText().getText().toString();
    }

    /** Clears a field's error as soon as the user edits it. */
    static void clearErrorOnEdit(TextInputLayout... fields) {
        for (TextInputLayout til : fields) {
            if (til.getEditText() == null) continue;
            til.getEditText().addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    til.setError(null);
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        }
    }

    static void showBanner(View banner, @Nullable CharSequence message) {
        if (message == null) {
            banner.setVisibility(View.GONE);
            return;
        }
        TextView tv = banner.findViewById(com.example.safesphere.R.id.tvError);
        tv.setText(message);
        banner.setVisibility(View.VISIBLE);
        banner.announceForAccessibility(message);
    }
}
