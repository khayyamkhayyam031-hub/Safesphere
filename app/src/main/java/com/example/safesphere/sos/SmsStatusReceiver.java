package com.example.safesphere.sos;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.utils.AppExecutors;

/** Receives the radio's "sent" and carrier's "delivered" callbacks for SOS SMS alerts. */
public class SmsStatusReceiver extends BroadcastReceiver {

    public static final String ACTION_SENT = "com.example.safesphere.SOS_SMS_SENT";
    public static final String ACTION_DELIVERED = "com.example.safesphere.SOS_SMS_DELIVERED";
    public static final String EXTRA_ATTEMPT_ID = "attempt_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        long attemptId = intent.getLongExtra(EXTRA_ATTEMPT_ID, -1);
        if (attemptId <= 0) return;
        int result = getResultCode();
        String status;
        String error = null;
        if (ACTION_DELIVERED.equals(intent.getAction())) {
            if (result != Activity.RESULT_OK) return; // no report is not a failure
            status = SafetyEvent.OP_DELIVERED;
        } else if (result == Activity.RESULT_OK) {
            status = SafetyEvent.OP_SENT;
        } else {
            status = SafetyEvent.OP_FAILED;
            error = describe(context, result);
        }

        Context app = context.getApplicationContext();
        PendingResult pending = goAsync();
        String finalError = error;
        AppExecutors.io(() -> {
            try {
                new EventRepository(app).updateAttemptStatus(attemptId, status, finalError);
            } finally {
                pending.finish();
            }
        });
    }

    private static String describe(Context context, int result) {
        switch (result) {
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return context.getString(R.string.sos_error_no_service);
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return context.getString(R.string.sos_error_radio_off);
            default:
                return context.getString(R.string.sos_error_generic, result);
        }
    }
}
