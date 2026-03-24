package net.shard.seconddawnrp.gmevent.data;

/**
 * A single configurable action attached to a Trigger Block.
 * payload format per type:
 *   BROADCAST         — "CHANNEL:message"  (CHANNEL = ALL, DIVISION:id, GM_ONLY)
 *   ACTIVATE_LINKED   — comma-separated env effect entry IDs
 *   DEACTIVATE_LINKED — comma-separated env effect entry IDs
 *   GENERATE_TASK     — "displayName|description|divisionName"
 *   NOTIFY_GM         — notification message text
 *   PLAY_SOUND        — "namespace:path:volume:pitch"
 */
public class TriggerAction {
    private final TriggerActionType type;
    private String payload;

    public TriggerAction(TriggerActionType type, String payload) {
        this.type    = type;
        this.payload = payload;
    }

    public TriggerActionType getType()     { return type; }
    public String getPayload()             { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}