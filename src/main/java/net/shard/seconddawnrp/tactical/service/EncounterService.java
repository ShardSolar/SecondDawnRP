package net.shard.seconddawnrp.tactical.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.shard.seconddawnrp.tactical.data.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages encounter lifecycle: create, addship, start, pause, resume, end.
 * Also manages the ship registry and shipyard spawn point.
 */
public class EncounterService {

    private final TacticalRepository repository;
    private MinecraftServer server;

    private final Map<String, EncounterState> activeEncounters = new ConcurrentHashMap<>();
    private final Map<String, ShipRegistryEntry> shipRegistry   = new ConcurrentHashMap<>();
    private final Map<String, List<HardpointEntry>> hardpoints  = new ConcurrentHashMap<>();

    private double shipyardX = 0, shipyardY = 64, shipyardZ = 0;
    private String shipyardWorldKey = "minecraft:overworld";

    public EncounterService(TacticalRepository repository) {
        this.repository = repository;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Startup ───────────────────────────────────────────────────────────────

    public void loadFromDatabase() {
        shipRegistry.clear();
        hardpoints.clear();

        for (ShipRegistryEntry entry : repository.loadAllShipRegistry()) {
            shipRegistry.put(entry.getShipId(), entry);
            List<HardpointEntry> hps = repository.loadHardpointsForShip(entry.getShipId());
            hardpoints.put(entry.getShipId(), hps);
        }

        repository.loadShipyard().ifPresent(coords -> {
            shipyardX = coords[0]; shipyardY = coords[1]; shipyardZ = coords[2];
        });
        repository.loadShipyardWorldKey().ifPresent(k -> shipyardWorldKey = k);

        System.out.println("[Tactical] Loaded " + shipRegistry.size() + " ship(s), "
                + hardpoints.values().stream().mapToInt(List::size).sum() + " hardpoint(s).");
    }

    // ── Encounter lifecycle ───────────────────────────────────────────────────

    public String createEncounter(String encounterId) {
        if (activeEncounters.containsKey(encounterId))
            return "Encounter '" + encounterId + "' already exists.";
        activeEncounters.put(encounterId, new EncounterState(encounterId));
        return "Encounter '" + encounterId + "' created. Add ships with /gm encounter addship.";
    }

    public String addShip(String encounterId, String shipId, String shipClass,
                          String faction, String controlMode) {
        EncounterState encounter = activeEncounters.get(encounterId);
        if (encounter == null) return "Encounter '" + encounterId + "' not found.";
        if (encounter.getStatus() == EncounterState.Status.ACTIVE)
            return "Cannot add ships to a running encounter. Pause first.";

        Optional<ShipClassDefinition> classDef = ShipClassDefinition.get(shipClass);
        if (classDef.isEmpty()) return "Unknown ship class: " + shipClass + ". Check data/seconddawnrp/ships/";

        ShipRegistryEntry reg = shipRegistry.get(shipId);
        String registryName = reg != null ? reg.getRegistryName() : shipId;
        double posX = reg != null ? reg.getDefaultPosX() : 0;
        double posZ = reg != null ? reg.getDefaultPosZ() : 0;
        float heading = reg != null ? reg.getDefaultHeading() : 0;

        ShipClassDefinition def = classDef.get();
        ShipState ship = new ShipState(shipId, registryName, shipClass,
                encounterId, faction.toUpperCase(),
                posX, posZ, heading,
                def.getHullMax(), def.getShieldMax(), def.getPowerCapacity());

        try {
            ship.setControlMode(ShipState.ControlMode.valueOf(controlMode.toUpperCase()));
        } catch (IllegalArgumentException e) {
            ship.setControlMode(ShipState.ControlMode.GM_MANUAL);
        }

        encounter.addShip(ship);
        return "Added " + registryName + " [" + ship.getCombatId() + "] to encounter '" + encounterId + "'.";
    }

    public String startEncounter(String encounterId) {
        EncounterState encounter = activeEncounters.get(encounterId);
        if (encounter == null) return "Encounter not found.";
        if (encounter.getShipCount() < 2) return "Need at least 2 ships to start.";
        if (!encounter.start()) return "Encounter is already active or ended.";

        broadcastToAll(Text.literal("[TACTICAL] ⚠ Encounter commenced — all hands to battle stations!")
                .formatted(Formatting.RED));
        return "Encounter '" + encounterId + "' started with " + encounter.getShipCount() + " ships.";
    }

    public String pauseEncounter(String encounterId) {
        EncounterState e = activeEncounters.get(encounterId);
        if (e == null) return "Encounter not found.";
        return e.pause() ? "Encounter paused." : "Could not pause — not active.";
    }

    public String resumeEncounter(String encounterId) {
        EncounterState e = activeEncounters.get(encounterId);
        if (e == null) return "Encounter not found.";
        return e.resume() ? "Encounter resumed." : "Could not resume — not paused.";
    }

    public String endEncounter(String encounterId, String reason) {
        EncounterState encounter = activeEncounters.get(encounterId);
        if (encounter == null) return "Encounter not found.";
        encounter.end(reason);
        activeEncounters.remove(encounterId);
        broadcastToAll(Text.literal("[TACTICAL] Encounter concluded: " + reason)
                .formatted(Formatting.YELLOW));
        return "Encounter '" + encounterId + "' ended.";
    }

    public String jumpShip(String encounterId, String shipId) {
        EncounterState encounter = activeEncounters.get(encounterId);
        if (encounter == null) return "Encounter not found.";
        Optional<ShipState> ship = encounter.getShip(shipId);
        if (ship.isEmpty()) return "Ship not found in encounter.";
        encounter.removeShip(shipId);
        encounter.log("[TACTICAL] " + ship.get().getRegistryName() + " made an emergency jump — escaped.");
        broadcastToAll(Text.literal("[TACTICAL] " + ship.get().getRegistryName()
                        + " executed emergency jump and escaped!")
                .formatted(Formatting.AQUA));
        return ship.get().getRegistryName() + " has been removed from the encounter.";
    }

    // ── Ship destruction / evacuation ─────────────────────────────────────────

    public void handleShipDestroyed(EncounterState encounter, ShipState ship) {
        ship.setDestroyed(true);
        encounter.log("[TACTICAL] " + ship.getRegistryName() + " [" + ship.getCombatId() + "] DESTROYED.");
        broadcastToAll(Text.literal("[TACTICAL] ⚠ " + ship.getRegistryName() + " has been destroyed!")
                .formatted(Formatting.DARK_RED));

        // Evacuate players to shipyard
        if (server == null) return;
        var worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(shipyardWorldKey));
        ServerWorld shipyard = server.getWorld(worldKey);
        if (shipyard == null) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Teleport all players — in full build would check crew assignment
            player.teleport(shipyard, shipyardX, shipyardY, shipyardZ, player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("[TACTICAL] Evacuation sequence initiated. Transporting to shipyard.")
                    .formatted(Formatting.RED), false);
        }
    }

    // ── Ship registry ─────────────────────────────────────────────────────────

    public String registerShip(String shipId, String registryName, String shipClass, String faction) {
        if (ShipClassDefinition.get(shipClass).isEmpty())
            return "Unknown ship class: " + shipClass;
        ShipRegistryEntry entry = new ShipRegistryEntry(shipId, registryName, shipClass, faction);
        shipRegistry.put(shipId, entry);
        hardpoints.put(shipId, new ArrayList<>());
        repository.saveShipRegistryEntry(entry);
        return "Ship '" + registryName + "' [" + shipId + "] registered as " + shipClass + ".";
    }

    /** Persist a ship registry entry — used when flags like isHomeShip change at runtime. */
    public void saveShipRegistryEntry(ShipRegistryEntry entry) {
        repository.saveShipRegistryEntry(entry);
    }

    public String unregisterShip(String shipId) {
        if (shipRegistry.remove(shipId) == null) return "Ship not found.";
        hardpoints.remove(shipId);
        repository.deleteShipRegistryEntry(shipId);
        return "Ship " + shipId + " removed from registry.";
    }

    // ── Hardpoints ────────────────────────────────────────────────────────────

    public String registerHardpoint(String shipId, net.minecraft.util.math.BlockPos pos,
                                    HardpointEntry.Arc arc, HardpointEntry.WeaponType type) {
        if (!shipRegistry.containsKey(shipId)) return "Ship not found: " + shipId;
        String hpId = shipId + "_" + type.name().toLowerCase() + "_" + pos.asLong();
        HardpointEntry hp = new HardpointEntry(hpId, shipId, pos, type, arc, 50, 20, 100);
        hardpoints.computeIfAbsent(shipId, k -> new ArrayList<>()).add(hp);
        repository.saveHardpoint(hp);
        return "Hardpoint registered: " + type.name() + " " + arc.name() + " on " + shipId;
    }

    public List<HardpointEntry> getHardpoints(String shipId) {
        return hardpoints.getOrDefault(shipId, List.of());
    }

    // ── Shipyard ──────────────────────────────────────────────────────────────

    public void setShipyard(String worldKey, double x, double y, double z) {
        this.shipyardWorldKey = worldKey;
        this.shipyardX = x;
        this.shipyardY = y;
        this.shipyardZ = z;
        repository.saveShipyard(worldKey, x, y, z);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<EncounterState> getEncounter(String encounterId) {
        return Optional.ofNullable(activeEncounters.get(encounterId));
    }

    public Collection<EncounterState> getAllEncounters() { return activeEncounters.values(); }
    public Map<String, ShipRegistryEntry> getShipRegistry() { return shipRegistry; }
    public Optional<ShipRegistryEntry> getShipEntry(String shipId) {
        return Optional.ofNullable(shipRegistry.get(shipId));
    }

    /** Find the encounter a given ship is participating in. */
    public Optional<EncounterState> getEncounterForShip(String shipId) {
        return activeEncounters.values().stream()
                .filter(e -> e.getShip(shipId).isPresent())
                .findFirst();
    }

    private void broadcastToAll(Text message) {
        if (server == null) return;
        server.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(message, false));
    }
}