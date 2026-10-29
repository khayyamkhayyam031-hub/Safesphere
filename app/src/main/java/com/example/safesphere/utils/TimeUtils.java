package com.example.safesphere.utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import com.example.safesphere.R;

import java.util.Date;
import java.util.Locale;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static String formatDateTime(Context context, long millis) {
        if (millis <= 0) return context.getString(R.string.common_not_available);
        return DateUtils.formatDateTime(context, millis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
    }

    public static String formatTime(Context context, long millis) {
        return DateFormat.getTimeFormat(context).format(new Date(millis));
    }

    /** "Just now", "5 minutes ago", ... */
    public static String relative(Context context, long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < DateUtils.MINUTE_IN_MILLIS) return context.getString(R.string.common_just_now);
        return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString();
    }

    /** mm:ss, or h:mm:ss once past an hour. */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
