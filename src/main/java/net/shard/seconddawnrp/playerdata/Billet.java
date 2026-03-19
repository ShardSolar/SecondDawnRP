package net.shard.seconddawnrp.playerdata;

public enum Billet {
    NONE("none"),

    // Command placeholders
    FIRST_OFFICER("first_officer"),
    EXECUTIVE_OFFICER("executive_officer"),
    COMMANDING_OFFICER("commanding_officer"),

    // Division chief placeholders
    CHIEF_MEDICAL_OFFICER("chief_medical_officer"),
    CHIEF_ENGINEER("chief_engineer"),
    CHIEF_OF_SECURITY("chief_of_security"),
    CHIEF_SCIENCE_OFFICER("chief_science_officer"),
    CHIEF_OPERATIONS_OFFICER("chief_operations_officer"),
    TACTICAL_OFFICER("tactical_officer"),

    // General placeholders
    DEPARTMENT_HEAD("department_head"),
    ASSISTANT_DEPARTMENT_HEAD("assistant_department_head"),
    WATCH_OFFICER("watch_officer");

    private final String id;

    Billet(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
