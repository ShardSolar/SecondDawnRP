package net.shard.seconddawnrp.gmevent.data;

import java.util.UUID;

/**
 * A registered Anomaly Marker Block entry.
 *
 * <p>Block registrations persist to JSON across restarts.
 * The {@code active} flag is session-only — always reset to {@code false}
 * on server start. GMs reactivate contacts each session via
 * {@code /gm anomaly activate <id>}.
 */
public class AnomalyEntry {

    private final String entryId;
    private final String worldKey;
    private final long blockPosLong;
    private final UUID registeredByUuid;

    private String name;
    private AnomalyType type;
    private String description;
    private boolean active;  // session-only

    public AnomalyEntry(String entryId, String worldKey, long blockPosLong,
                        UUID registeredByUuid, String name, AnomalyType type) {
        this.entryId          = entryId;
        this.worldKey         = worldKey;
        this.blockPosLong     = blockPosLong;
        this.registeredByUuid = registeredByUuid;
        this.name             = name;
        this.type             = type;
        this.description      = "";
        this.active           = false;
    }

    public void setName(String name)        { this.name = name; }
    public void setType(AnomalyType type)   { this.type = type; }
    public void setDescription(String desc) { this.description = desc; }
    public void setActive(boolean active)   { this.active = active; }

    public String getEntryId()        { return entryId; }
    public String getWorldKey()       { return worldKey; }
    public long getBlockPosLong()     { return blockPosLong; }
    public UUID getRegisteredByUuid() { return registeredByUuid; }
    public String getName()           { return name; }
    public AnomalyType getType()      { return type; }
    public String getDescription()    { return description; }
    public boolean isActive()         { return active; }
}