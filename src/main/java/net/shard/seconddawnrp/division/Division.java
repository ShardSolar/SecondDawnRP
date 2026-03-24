package net.shard.seconddawnrp.division;

public enum Division {
    COMMAND("command"),
    OPERATIONS("operations"),
    ENGINEERING("engineering"),
    SCIENCE("science"),
    UNASSIGNED("unassigned"),
    SECURITY("security"),
    MEDICAL("medical");


    private final String id;

    Division(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}