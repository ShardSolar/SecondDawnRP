package net.shard.seconddawnrp.terminal;

/**
 * All terminal types that can be designated onto any world block.
 * Each type maps to an existing screen/handler pair and a LuckPerms permission node.
 *
 * Phases noted — types for future phases compile fine but their screens are
 * not wired yet (openScreen returns a "not yet available" message).
 */
public enum TerminalDesignatorType {

    // Phase 1 — complete
    OPS_TERMINAL(
            "Operations Terminal",
            "seconddawnrp.terminal.ops",
            0x4A90D9,   // blue
            true
    ),

    // Phase 4 — complete
    ENGINEERING_CONSOLE(
            "Engineering Console",
            "seconddawnrp.terminal.engineering",
            0xF5A623,   // amber
            true
    ),

    // Phase 4.75 — Library is planned but simple enough to wire now
    LIBRARY_TERMINAL(
            "Library Terminal",
            "seconddawnrp.terminal.library",
            0x9B59B6,   // purple
            false       // screen not yet built
    ),

    // Phase 8 — planned
    MEDICAL_CONSOLE(
            "Medical Console",
            "seconddawnrp.terminal.medical",
            0x2ECC71,   // green
            false
    ),

    // Phase 9 — planned
    SECURITY_CONSOLE(
            "Security Console",
            "seconddawnrp.terminal.security",
            0xE74C3C,   // red
            false
    ),

    // Phase 10 — planned
    SCIENCE_CONSOLE(
            "Science Console",
            "seconddawnrp.terminal.science",
            0x1ABC9C,   // teal
            false
    ),

    // Phase 6.5 — planned
    MISSION_CONSOLE(
            "Mission Console",
            "seconddawnrp.terminal.mission",
            0xF39C12,   // orange
            false
    ),

    // Phase 6.5 — planned
    RESOURCE_TERMINAL(
            "Resource Terminal",
            "seconddawnrp.terminal.resource",
            0x95A5A6,   // grey
            false
    ),

    // Phase 12 — planned
    TACTICAL_CONSOLE(
            "Tactical Console",
            "seconddawnrp.terminal.tactical",
            0xC0392B,   // dark red
            false
    );

    // -------------------------------------------------------------------------

    private final String displayName;
    private final String permissionNode;
    private final int glowColor;      // ARGB int for particle tint
    private final boolean implemented; // false = "not yet available" message on interact

    TerminalDesignatorType(String displayName, String permissionNode, int glowColor, boolean implemented) {
        this.displayName   = displayName;
        this.permissionNode = permissionNode;
        this.glowColor     = glowColor;
        this.implemented   = implemented;
    }

    public String getDisplayName()   { return displayName; }
    public String getPermissionNode() { return permissionNode; }
    public int    getGlowColor()      { return glowColor; }
    public boolean isImplemented()    { return implemented; }
}