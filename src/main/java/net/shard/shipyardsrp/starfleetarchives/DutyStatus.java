package net.shard.shipyardsrp.starfleetarchives;

public enum DutyStatus {
    OFF_DUTY("off_duty"),
    ON_DUTY("on_duty");

    private final String id;

    DutyStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}