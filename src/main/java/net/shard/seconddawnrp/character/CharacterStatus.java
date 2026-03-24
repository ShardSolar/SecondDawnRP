package net.shard.seconddawnrp.character;

public enum CharacterStatus {
    /** Active crew member. Full participation. */
    ACTIVE,
    /** Character is deceased. Profile locked to read-only. */
    DECEASED,
    /** Spectator state — can observe but not participate. */
    SPECTATOR
}