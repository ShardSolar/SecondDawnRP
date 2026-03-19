package net.shard.seconddawnrp.playerdata;

public enum ProgressionPath {
    ENLISTED("enlisted"),
    COMMISSIONED("commissioned");

    private final String id;

    ProgressionPath(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
