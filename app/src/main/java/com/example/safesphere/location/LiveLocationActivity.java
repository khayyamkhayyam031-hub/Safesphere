package com.example.safesphere.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.R;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;
import com.example.safesphere.utils.UiUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Location screen without a map SDK: shows the actual fix, its accuracy and age, and hands off
 * to the user's maps app. Live tracking runs only while this screen is open and is shown to the
 * user only; nothing is sent to anyone.
 */
public class LiveLocationActivity extends AppCompatActivity {

    private Location location;
    private boolean fresh;
    private boolean locating;
    private boolean tracking;
    private Runnable cancelRequest;
    private final LocationListener trackListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location l) {
            location = l;
            fresh = true;
            bind();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            bind();
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_location);
        UiUtils.setupToolbar(this, R.string.location_title);
        findViewById(R.id.btnOpenMap).setOnClickListener(v -> {
            if (location != null) UiUtils.openMap(this, location.getLatitude(), location.getLongitude());
        });
        findViewById(R.id.btnTrack).setOnClickListener(v -> {
            if (tracking) stopTracking();
            else startTracking();
            bind();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        location = LocationHelper.getLastKnown(this);
        fresh = false;
        bind();
        if (LocationHelper.hasPermission(this) && LocationHelper.isLocationEnabled(this)) refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopTracking();
        if (cancelRequest != null) cancelRequest.run();
        locating = false;
    }

    private void refresh() {
        if (locating) return;
        locating = true;
        bind();
        cancelRequest = LocationHelper.requestFresh(this, 20_000L, (l, isFresh) -> {
            locating = false;
            if (isFinishing() || isDestroyed()) return;
            if (l != null) {
                location = l;
                fresh = isFresh;
            }
            bind();
        });
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null || !LocationHelper.hasPermission(this)) return;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 5000L, 5f, trackListener);
            }
            tracking = true;
        } catch (SecurityException | IllegalArgumentException e) {
            tracking = false;
        }
    }

    private void stopTracking() {
        if (!tracking) return;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null) lm.removeUpdates(trackListener);
        tracking = false;
    }

    private void bind() {
        boolean permission = LocationHelper.hasPermission(this);
        boolean enabled = LocationHelper.isLocationEnabled(this);
        TextView chip = findViewById(R.id.tvChip);
        TextView coords = findViewById(R.id.tvCoords);
        TextView meta = findViewById(R.id.tvMeta);
        Button primary = findViewById(R.id.btnPrimary);
        Button track = findViewById(R.id.btnTrack);
        findViewById(R.id.pbLocating).setVisibility(locating ? View.VISIBLE : View.INVISIBLE);

        if (location != null) {
            coords.setText(String.format(Locale.US, "%.5f\n%.5f", location.getLatitude(), location.getLongitude()));
            coords.setContentDescription(getString(R.string.location_coords_cd, location.getLatitude(), location.getLongitude()));
            if (fresh) {
                StatusUi.apply(chip, getString(tracking ? R.string.location_chip_tracking : R.string.location_chip_current), StatusUi.Tone.SUCCESS);
                meta.setText(getString(R.string.location_meta_current, TimeUtils.relative(this, location.getTime()), Math.round(location.getAccuracy())));
            } else {
                StatusUi.apply(chip, getString(R.string.status_last_known), StatusUi.Tone.WARNING);
                meta.setText(getString(R.string.location_meta_last_known, TimeUtils.relative(this, location.getTime()), Math.round(location.getAccuracy())));
            }
        } else {
            coords.setText(R.string.location_none_yet);
            coords.setContentDescription(null);
            StatusUi.apply(chip, getString(R.string.status_unavailable), StatusUi.Tone.NEUTRAL);
            meta.setText(!permission ? R.string.location_permission_reason
                    : !enabled ? R.string.status_location_disabled
                    : locating ? R.string.location_locating : R.string.location_tap_refresh);
        }

        row(R.id.rowPermission, getString(R.string.location_row_permission),
                permission ? getString(R.string.location_permission_granted) : getString(R.string.location_permission_missing),
                permission ? getString(R.string.status_chip_ready) : getString(R.string.status_chip_off),
                permission ? StatusUi.Tone.SUCCESS : StatusUi.Tone.WARNING);
        row(R.id.rowAccuracy, getString(R.string.location_row_accuracy),
                location != null ? getString(R.string.location_accuracy_value, Math.round(location.getAccuracy())) : getString(R.string.common_not_available),
                null, null);
        row(R.id.rowUpdated, getString(R.string.location_row_updated),
                location != null ? TimeUtils.formatDateTime(this, location.getTime()) : getString(R.string.common_not_available),
                null, null);
        row(R.id.rowSource, getString(R.string.location_row_source),
                location != null && location.getProvider() != null ? location.getProvider().toUpperCase(Locale.US) : getString(R.string.common_not_available),
                null, null);

        if (!permission) {
            primary.setText(R.string.location_allow);
            primary.setOnClickListener(v -> permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}));
        } else if (!enabled) {
            primary.setText(R.string.home_fix_location_on);
            primary.setOnClickListener(v -> UiUtils.openLocationSettings(this));
        } else {
            primary.setText(locating ? R.string.location_locating : R.string.location_refresh);
            primary.setOnClickListener(v -> refresh());
        }
        primary.setEnabled(!locating);
        track.setVisibility(permission && enabled ? View.VISIBLE : View.GONE);
        track.setText(tracking ? R.string.location_stop_tracking : R.string.location_start_tracking);
        findViewById(R.id.btnOpenMap).setEnabled(location != null);
    }

    private void row(int id, String label, String value, @Nullable String chip, @Nullable StatusUi.Tone tone) {
        View row = findViewById(id);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvValue)).setText(value);
        TextView c = row.findViewById(R.id.tvChip);
        c.setVisibility(chip == null ? View.GONE : View.VISIBLE);
        if (chip != null) StatusUi.apply(c, chip, tone);
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (!LocationHelper.hasPermission(this) && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            UiUtils.openAppSettings(this);
            return;
        }
        location = LocationHelper.getLastKnown(this);
        bind();
        if (LocationHelper.hasPermission(this)) refresh();
    }
}
