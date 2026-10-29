package com.example.safesphere.data;

/** Metadata for an audio recording kept in private app storage. */
public class EvidenceItem {
    public long id;
    public int userId;
    /** SOS event this recording belongs to, or 0. */
    public long eventId;
    public String filePath;
    public long durationMs;
    public String status;
    public long createdAt;
}
