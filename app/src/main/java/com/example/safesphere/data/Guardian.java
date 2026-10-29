package com.example.safesphere.data;

public class Guardian {
    public static final int TIER_PRIMARY = 1;
    public static final int TIER_SECONDARY = 2;

    /** Guardians are stored locally only; nobody is invited or notified when one is added. */
    public static final String INVITE_NOT_INVITED = "NOT_INVITED";

    public long id;
    public int userId;
    public String name;
    public String phoneNumber;
    public String relationship;
    public int tier = TIER_PRIMARY;
    public String inviteStatus = INVITE_NOT_INVITED;
    public long createdAt;
    public long updatedAt;

    public String getInitial() {
        return name == null || name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase();
    }
}
