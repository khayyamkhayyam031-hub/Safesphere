package com.example.safesphere.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.safesphere.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

/** Small helpers shared by several screens. */
public final class UiUtils {

    private UiUtils() {
    }

    public static boolean hasPermission(Context c, String permission) {
        return ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static void openAppSettings(Context c) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", c.getPackageName(), null));
        startSafely(c, intent);
    }

    public static void openLocationSettings(Context c) {
        startSafely(c, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    /** Opens the dialer with the number filled in; the user still has to press call. */
    public static void dial(Context c, String number) {
        startSafely(c, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))));
    }

    public static void openMap(Context c, double lat, double lng) {
        Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse(com.example.safesphere.location.LocationHelper.geoUri(lat, lng)));
        if (geo.resolveActivity(c.getPackageManager()) != null) {
            c.startActivity(geo);
        } else {
            startSafely(c, new Intent(Intent.ACTION_VIEW,
                    Uri.parse(com.example.safesphere.location.LocationHelper.mapsUrl(lat, lng))));
        }
    }

    /** Opens the user's SMS app with a draft. Nothing is sent unless the user sends it. */
    public static void composeSms(Context c, String recipients, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + recipients));
        intent.putExtra("sms_body", body);
        startSafely(c, intent);
    }

    public static void startSafely(Context c, Intent intent) {
        try {
            c.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, R.string.common_no_app, Toast.LENGTH_LONG).show();
        }
    }

    public static void setupToolbar(AppCompatActivity activity, @StringRes int title) {
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> activity.getOnBackPressedDispatcher().onBackPressed());
    }

    /** Shows the user's photo or their initial. */
    public static void bindAvatar(ImageView image, TextView initial, @Nullable String photoPath, String letter) {
        Bitmap bitmap = null;
        if (photoPath != null && new File(photoPath).exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            bitmap = BitmapFactory.decodeFile(photoPath, opts);
        }
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
            image.setVisibility(View.VISIBLE);
            initial.setVisibility(View.GONE);
        } else {
            image.setVisibility(View.GONE);
            initial.setVisibility(View.VISIBLE);
            initial.setText(letter);
        }
    }

    public static void showInfo(Context c, @StringRes int title, @StringRes int message) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.common_ok, null)
                .show();
    }

    public static void showPrivacyPolicy(Context c) {
        showInfo(c, R.string.privacy_policy_title, R.string.privacy_policy_body);
    }

    public static void showTerms(Context c) {
        showInfo(c, R.string.terms_title, R.string.terms_body);
    }
}
