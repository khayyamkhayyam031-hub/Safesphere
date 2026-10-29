package com.example.safesphere.utils;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.safesphere.R;

/** Binds the shared {@code view_empty_state} layout. */
public final class EmptyState {

    private EmptyState() {
    }

    public static void bind(View root, @DrawableRes int icon, @StringRes int title, @StringRes int message,
                            @StringRes int action, @Nullable View.OnClickListener onAction) {
        ((ImageView) root.findViewById(R.id.ivEmptyIcon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.tvEmptyTitle)).setText(title);
        ((TextView) root.findViewById(R.id.tvEmptyMessage)).setText(message);
        Button button = root.findViewById(R.id.btnEmptyAction);
        if (onAction != null && action != 0) {
            button.setText(action);
            button.setOnClickListener(onAction);
            button.setVisibility(View.VISIBLE);
        } else {
            button.setVisibility(View.GONE);
        }
    }
}
