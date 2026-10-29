package com.example.safesphere.utils;

import androidx.annotation.Nullable;

/**
 * Input rules shared by the auth and guardian screens.
 *
 * <p>Phone numbers: Pakistani mobile numbers in any common form ({@code 0300 1234567},
 * {@code 3001234567}, {@code +92 300 1234567}, {@code 0092...}) are normalised to
 * {@code +923001234567}. Other countries are accepted in international form ({@code +} followed
 * by 8–15 digits).
 *
 * <p>Passwords: at least 8 characters, with at least one letter and one digit.
 */
public final class ValidationUtils {

    public static final int PASSWORD_MIN_LENGTH = 8;

    private ValidationUtils() {
    }

    /** Returns the normalised number, or null if it is not a valid phone number. */
    @Nullable
    public static String normalizePhone(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s\\-().]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        if (s.matches("03\\d{9}")) return "+92" + s.substring(1);
        if (s.matches("3\\d{9}")) return "+92" + s;
        if (s.matches("\\+923\\d{9}")) return s;
        if (s.startsWith("+92")) return null; // Pakistani prefix but not a mobile number
        if (s.matches("\\+[1-9]\\d{7,14}")) return s;
        return null;
    }

    public static boolean isValidPassword(@Nullable CharSequence password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) return false;
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) letter = true;
            else if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    public static boolean isValidName(@Nullable String name) {
        return name != null && name.trim().length() >= 2 && name.trim().length() <= 60;
    }

    /** Reads a text field into a char array, so passwords never become immutable Strings. */
    public static char[] toChars(@Nullable CharSequence text) {
        if (text == null) return new char[0];
        char[] out = new char[text.length()];
        for (int i = 0; i < out.length; i++) out[i] = text.charAt(i);
        return out;
    }
}
