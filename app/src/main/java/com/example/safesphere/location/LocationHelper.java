package com.example.safesphere.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper over the platform {@link LocationManager} (no Play Services dependency).
 * Callers always learn whether a fix is fresh or only the last known one.
 */
public final class LocationHelper {

    /** A fix at least this accurate ends a fresh request early. */
    private static final float GOOD_ACCURACY_M = 50f;

    public interface Callback {
        /** Called on the main thread. {@code fresh} is false when only a cached fix was found. */
        void onResult(@Nullable Location location, boolean fresh);
    }

    private LocationHelper() {
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return lm != null && LocationManagerCompat.isLocationEnabled(lm);
    }

    /** Newest cached fix from any provider, or null. May be old: check {@link Location#getTime()}. */
    @SuppressLint("MissingPermission")
    @Nullable
    public static Location getLastKnown(Context context) {
        if (!hasPermission(context)) return null;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Provider vanished or permission was revoked mid-call.
            }
        }
        return best;
    }

    /**
     * Asks GPS and network providers for a new fix. Finishes on the first accurate fix or at
     * {@code timeoutMs}, returning the best fresh fix seen, else the last known one (fresh=false).
     * Returns a handle that cancels the request.
     */
    @SuppressLint("MissingPermission")
    public static Runnable requestFresh(Context context, long timeoutMs, @NonNull Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !hasPermission(app) || !isLocationEnabled(app)) {
            main.post(() -> callback.onResult(getLastKnown(app), false));
            return () -> { };
        }

        final Location[] best = {null};
        final boolean[] done = {false};
        final List<LocationListener> listeners = new ArrayList<>();

        Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            for (LocationListener l : listeners) lm.removeUpdates(l);
            if (best[0] != null) callback.onResult(best[0], true);
            else callback.onResult(getLastKnown(app), false);
        };

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (best[0] == null || location.getAccuracy() < best[0].getAccuracy()) best[0] = location;
                if (location.getAccuracy() <= GOOD_ACCURACY_M) finish.run();
            }

            // Implemented explicitly: these are abstract on Android 10 and older.
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }
        };
        listeners.add(listener);

        boolean requested = false;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Provider not present on this device.
            }
        }
        if (!requested) {
            main.post(finish);
        } else {
            main.postDelayed(finish, timeoutMs);
        }
        return () -> main.post(() -> {
            if (done[0]) return;
            done[0] = true;
            for (LocationListener l : listeners) lm.removeUpdates(l);
        });
    }

    public static String mapsUrl(double lat, double lng) {
        return String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lng);
    }

    public static String geoUri(double lat, double lng) {
        return String.format(java.util.Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lng, lat, lng);
    }
}
