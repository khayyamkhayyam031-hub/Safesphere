package com.example.safesphere.utils;

import android.os.Build;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 password hashing using the platform's {@link SecretKeyFactory}.
 *
 * <p>Each account gets its own random 16-byte salt. The stored hash records the algorithm and
 * iteration count ({@code algorithm$iterations$hashHex}) so they can be raised later without
 * breaking existing accounts. PBKDF2-HMAC-SHA256 is used on Android 8.0+, where the platform
 * provides it; older devices fall back to PBKDF2-HMAC-SHA1, which is still a sound PBKDF2 PRF.
 */
public final class PasswordHasher {

    private static final String ALG_SHA256 = "PBKDF2WithHmacSHA256";
    private static final String ALG_SHA1 = "PBKDF2WithHmacSHA1";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static final class Hashed {
        public final String hash;
        public final String salt;

        Hashed(String hash, String salt) {
            this.hash = hash;
            this.salt = salt;
        }
    }

    public static Hashed hash(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String algorithm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? ALG_SHA256 : ALG_SHA1;
        byte[] derived = derive(algorithm, password, salt, ITERATIONS);
        return new Hashed(algorithm + "$" + ITERATIONS + "$" + toHex(derived), toHex(salt));
    }

    public static boolean verify(char[] password, String storedHash, String saltHex)
            throws GeneralSecurityException {
        if (storedHash == null || saltHex == null) return false;
        String[] parts = storedHash.split("\\$");
        if (parts.length != 3) return false;
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] expected = fromHex(parts[2]);
        byte[] actual = derive(parts[0], password, fromHex(saltHex), iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    /** Spends the same work as a real check, so a missing account is not faster to reject. */
    public static void burnEquivalentWork(char[] password) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            String algorithm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? ALG_SHA256 : ALG_SHA1;
            derive(algorithm, password, salt, ITERATIONS);
        } catch (GeneralSecurityException ignored) {
            // Nothing to protect if hashing itself is unavailable.
        }
    }

    private static byte[] derive(String algorithm, char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        if (len % 2 != 0) return new byte[0];
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    public static void wipe(char[] chars) {
        if (chars != null) Arrays.fill(chars, '\0');
    }
}
