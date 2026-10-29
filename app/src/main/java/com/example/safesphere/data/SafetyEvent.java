package com.example.safesphere.data;

/**
 * One row of the local safety history: an SOS, a journey, a fake call or an evidence recording.
 * Status fields hold the {@code Status} constants below and always describe what actually happened.
 */
public class SafetyEvent {

    public static final String TYPE_SOS = "SOS";
    public static final String TYPE_JOURNEY = "JOURNEY";
    public static final String TYPE_FAKE_CALL = "FAKE_CALL";
    public static final String TYPE_EVIDENCE = "EVIDENCE";

    public static final String TRIGGER_BUTTON = "BUTTON";
    public static final String TRIGGER_SHAKE = "SHAKE";
    public static final String TRIGGER_JOURNEY = "JOURNEY";

    // Event lifecycle
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELAYED = "DELAYED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    // Per-operation status
    public static final String OP_NOT_STARTED = "NOT_STARTED";
    public static final String OP_PENDING = "PENDING";
    public static final String OP_IN_PROGRESS = "IN_PROGRESS";
    public static final String OP_SENT = "SENT";
    public static final String OP_DELIVERED = "DELIVERED";
    public static final String OP_PARTIAL = "PARTIAL";
    public static final String OP_FAILED = "FAILED";
    public static final String OP_UNAVAILABLE = "UNAVAILABLE";
    public static final String OP_COMPLETED = "COMPLETED";
    /** Location came from the last known fix, not a fresh one. */
    public static final String OP_LAST_KNOWN = "LAST_KNOWN";
    public static final String OP_SAVED_LOCALLY = "SAVED_LOCALLY";

    public long id;
    public int userId;
    public String type;
    public String trigger;
    public String status;
    public long startedAt;
    public long endedAt;

    public boolean hasLocation;
    public double latitude;
    public double longitude;
    public float accuracyMeters;
    public long locationTime;
    public String locationStatus = OP_NOT_STARTED;
    public String alertStatus = OP_NOT_STARTED;
    public String evidenceStatus = OP_NOT_STARTED;

    public String destination;
    public long expectedArrival;
    public String notes;

    public boolean isOpen() {
        return STATUS_ACTIVE.equals(status) || STATUS_DELAYED.equals(status);
    }
}
