package net.shard.seconddawnrp.gmevent.data;

public enum AnomalyType {

    /**
     * Energy reading — possible weapon or power source.
     * Alert: YELLOW. Notifies: Security + Science.
     */
    ENERGY("Energy Reading", false,
            "Energy reading detected. Sensor interference in vicinity."),

    /**
     * Biosignature — possible contamination.
     * Alert: YELLOW. Notifies: Medical + Security + Science.
     */
    BIOLOGICAL("Biosignature", false,
            "Biosignature detected. Possible contamination — Medical notified."),

    /**
     * Gravitational anomaly — navigation hazard.
     * Alert: YELLOW. Notifies: Science + Command.
     */
    GRAVITATIONAL("Gravitational Anomaly", false,
            "Gravitational distortion detected. Navigation hazard — mark no-go zone."),

    /**
     * Unidentified contact — maximum tension.
     * Alert: RED. Notifies: ALL divisions.
     */
    UNKNOWN("Unidentified Contact", true,
            "Unidentified contact — all divisions respond immediately.");

    private final String displayName;
    private final boolean criticalAlert;      // true = RED, false = YELLOW
    private final String broadcastMessage;

    AnomalyType(String displayName, boolean criticalAlert, String broadcastMessage) {
        this.displayName      = displayName;
        this.criticalAlert    = criticalAlert;
        this.broadcastMessage = broadcastMessage;
    }

    public String getDisplayName()      { return displayName; }
    public boolean isCriticalAlert()    { return criticalAlert; }
    public String getBroadcastMessage() { return broadcastMessage; }
}