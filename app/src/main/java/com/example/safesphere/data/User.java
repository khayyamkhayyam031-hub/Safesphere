package com.example.safesphere.data;

/**
 * A local SafeHer account. Password hashes are deliberately not part of this model:
 * they stay inside {@link com.example.safesphere.database.UserRepository}.
 */
public class User {
    public int id;
    public String fullName;
    public String phoneNumber;
    public String profilePhotoPath;
    public boolean profileCompleted;
    public long createdAt;
    public long updatedAt;

    public User() {
    }

    public String getFirstName() {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    public String getInitial() {
        String first = getFirstName();
        return first.isEmpty() ? "?" : first.substring(0, 1).toUpperCase();
    }
}
