package com.example.safesphere.data;

/** One SMS alert to one guardian for one SOS event. */
public class AlertAttempt {
    public long id;
    public long eventId;
    public String guardianName;
    public String phoneNumber;
    /** One of the {@code SafetyEvent.OP_*} constants. */
    public String status;
    public String error;
    public long updatedAt;
}
