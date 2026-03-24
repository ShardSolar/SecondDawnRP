package net.shard.seconddawnrp.gmevent.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.gmevent.data.AnomalyEntry;
import net.shard.seconddawnrp.gmevent.data.AnomalyType;
import net.shard.seconddawnrp.gmevent.repository.JsonAnomalyRepository;

import java.util.*;

/**
 * Service for Anomaly Marker Blocks.
 *
 * <p>Handles registration, activation/deactivation, and division notifications.
 * Active contacts are session-only — cleared on server stop.
 *
 * <p>On activation, broadcasts a sensor alert to relevant divisions based
 * on anomaly type, formatted to feel like a ship computer notification.
 */
public class AnomalyService {

    private final JsonAnomalyRepository repository;
    private final Map<String, AnomalyEntry> entries = new LinkedHashMap<>();

    public AnomalyService(JsonAnomalyRepository repository) {
        this.repository = repository;
    }

    public void reload() {
        entries.clear();
        // Active flag always resets — loaded entries start inactive
        for (AnomalyEntry e : repository.loadAll()) {
            entries.put(e.getEntryId(), e);
        }
        System.out.println("[SecondDawnRP] Loaded " + entries.size() + " anomaly markers.");
    }

    public void saveAll() {
        repository.save(entries.values());
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public AnomalyEntry register(String worldKey, long blockPosLong,
                                 UUID registeredBy, String name, AnomalyType type) {
        boolean exists = entries.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong);
        if (exists) throw new IllegalStateException("Already registered at this position.");

        String id = "anm_" + Long.toHexString(blockPosLong & 0xFFFFFFL)
                + "_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFL);
        AnomalyEntry entry = new AnomalyEntry(id, worldKey, blockPosLong, registeredBy, name, type);
        entries.put(id, entry);
        repository.save(entries.values());
        return entry;
    }

    public boolean unregister(String worldKey, long blockPosLong) {
        Optional<AnomalyEntry> opt = entries.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
        if (opt.isEmpty()) return false;
        entries.remove(opt.get().getEntryId());
        repository.save(entries.values());
        return true;
    }

    // ── Activation ────────────────────────────────────────────────────────────

    /**
     * Activates an anomaly contact — broadcasts sensor alert to relevant
     * divisions and marks the contact active for the sensor network.
     */
    public boolean activate(String entryId, MinecraftServer server) {
        AnomalyEntry e = entries.get(entryId);
        if (e == null) return false;
        e.setActive(true);
        broadcastActivation(e, server);
        // No save — active is session-only
        return true;
    }

    public boolean deactivate(String entryId) {
        AnomalyEntry e = entries.get(entryId);
        if (e == null) return false;
        e.setActive(false);
        return true;
    }

    public void saveEntry(AnomalyEntry entry) {
        entries.put(entry.getEntryId(), entry);
        repository.save(entries.values());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Collection<AnomalyEntry> getAll()                  { return entries.values(); }
    public Optional<AnomalyEntry> getById(String id)          { return Optional.ofNullable(entries.get(id)); }
    public List<AnomalyEntry> getActiveContacts() {
        return entries.values().stream().filter(AnomalyEntry::isActive).toList();
    }
    public Optional<AnomalyEntry> getByPosition(String wk, long pos) {
        return entries.values().stream()
                .filter(e -> e.getWorldKey().equals(wk) && e.getBlockPosLong() == pos)
                .findFirst();
    }

    // ── Division notifications ────────────────────────────────────────────────

    private void broadcastActivation(AnomalyEntry entry, MinecraftServer server) {
        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
        boolean critical = entry.getType().isCriticalAlert();

        // Header alert line
        Text header = Text.literal(
                        "[ SENSOR ALERT — " + (critical ? "RED" : "YELLOW") + " ]  "
                                + entry.getType().getDisplayName().toUpperCase()
                                + ": " + entry.getName())
                .formatted(critical ? Formatting.RED : Formatting.YELLOW);

        // Detail line
        Text detail = Text.literal("  " + entry.getType().getBroadcastMessage()
                        + "  |  Pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .formatted(Formatting.GRAY);

        // Description if set
        Text desc = entry.getDescription().isBlank() ? null
                : Text.literal("  \"" + entry.getDescription() + "\"")
                .formatted(Formatting.DARK_GRAY);

        // Determine target divisions
        Set<Division> targets = notifyDivisions(entry.getType());

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean isGm = player.hasPermissionLevel(2)
                    || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.gm.use");

            // GMs always see all anomaly alerts
            if (isGm) {
                send(player, header, detail, desc);
                continue;
            }

            // Division-targeted players
            var profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
            if (profile != null && profile.getDivision() != null
                    && targets.contains(profile.getDivision())) {
                send(player, header, detail, desc);
            }
        }
    }

    private void send(ServerPlayerEntity player, Text header, Text detail, Text desc) {
        player.sendMessage(header, false);
        player.sendMessage(detail, false);
        if (desc != null) player.sendMessage(desc, false);
    }

    private static Set<Division> notifyDivisions(AnomalyType type) {
        return switch (type) {
            case ENERGY        -> Set.of(Division.SECURITY, Division.SCIENCE);
            case BIOLOGICAL    -> Set.of(Division.MEDICAL, Division.SECURITY, Division.SCIENCE);
            case GRAVITATIONAL -> Set.of(Division.SCIENCE, Division.COMMAND);
            case UNKNOWN       -> Set.of(Division.values()); // all divisions
        };
    }
}