package net.shard.shipyardsrp.starfleetarchives;

public enum Division {
    COMMAND("command"),
    OPERATIONS("operations"),
    SCIENCE("science"),
    UNASSIGNED("unassigned");

    private final String id;

    Division(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}