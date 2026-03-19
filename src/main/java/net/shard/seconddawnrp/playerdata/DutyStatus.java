package net.shard.seconddawnrp.playerdata;

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