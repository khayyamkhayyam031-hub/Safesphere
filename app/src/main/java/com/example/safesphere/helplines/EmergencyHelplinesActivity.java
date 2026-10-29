package com.example.safesphere.helplines;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.utils.UiUtils;

/**
 * Emergency numbers supplied for this project (Pakistan). Call opens the dialer with the number
 * filled in, so the user always presses call themselves; the app holds no CALL_PHONE permission.
 * Verify each service for the release region.
 */
public class EmergencyHelplinesActivity extends AppCompatActivity {

    private static final int[][] HELPLINES = {
            // number, name, description
            {R.string.helpline_police_number, R.string.helpline_police_name, R.string.helpline_police_desc},
            {R.string.helpline_rescue_number, R.string.helpline_rescue_name, R.string.helpline_rescue_desc},
            {R.string.helpline_women_number, R.string.helpline_women_name, R.string.helpline_women_desc},
            {R.string.helpline_child_number, R.string.helpline_child_name, R.string.helpline_child_desc},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_helplines);
        UiUtils.setupToolbar(this, R.string.helplines_title);

        LinearLayout list = findViewById(R.id.list);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int[] h : HELPLINES) {
            String number = getString(h[0]);
            String name = getString(h[1]);
            View item = inflater.inflate(R.layout.item_helpline, list, false);
            ((TextView) item.findViewById(R.id.tvNumber)).setText(number);
            ((TextView) item.findViewById(R.id.tvName)).setText(name);
            ((TextView) item.findViewById(R.id.tvDescription)).setText(h[2]);
            View call = item.findViewById(R.id.btnCall);
            call.setContentDescription(getString(R.string.helplines_call_cd, name, number));
            call.setOnClickListener(v -> UiUtils.dial(this, number));
            list.addView(item);
        }
    }
}
