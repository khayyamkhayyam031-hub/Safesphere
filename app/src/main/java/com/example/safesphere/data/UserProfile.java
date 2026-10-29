package com.example.safesphere.data;

/** Optional, private profile details. Never shared with guardians. */
public class UserProfile {
    public int id;
    public int userId;
    public String bloodGroup;
    public String medicalNotes;
    public long createdAt;
    public long updatedAt;

    public UserProfile() {
    }
}
