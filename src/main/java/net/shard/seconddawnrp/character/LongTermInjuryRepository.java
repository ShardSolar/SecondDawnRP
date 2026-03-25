package net.shard.seconddawnrp.character;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// ── Repository interface ──────────────────────────────────────────────────────

public interface LongTermInjuryRepository {
    Optional<LongTermInjury> loadActive(UUID playerUuid);
    void save(LongTermInjury injury);
    void deactivate(String injuryId);
    /** Load all active injuries — used on server start to restore tick refresh state. */
    List<LongTermInjury> loadAllActive();
}

// ── SQLite implementation ─────────────────────────────────────────────────────

