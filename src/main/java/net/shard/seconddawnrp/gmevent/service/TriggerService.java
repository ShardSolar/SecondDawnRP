package net.shard.seconddawnrp.gmevent.service;

import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.gmevent.data.*;
import net.shard.seconddawnrp.gmevent.repository.JsonTriggerRepository;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;

import java.util.*;

public class TriggerService {

    private final JsonTriggerRepository repository;

    private final Map<String, TriggerEntry> entries = new LinkedHashMap<>();
    private final Map<String, Long> cooldownExpiry = new HashMap<>();
    private final Map<String, Set<UUID>> playersInRadius = new HashMap<>();

    private long serverTick = 0;

    public TriggerService(JsonTriggerRepository repository) {
        this.repository = repository;
    }

    public void reload() {
        entries.clear();
        for (TriggerEntry e : repository.loadAll()) {
            entries.put(e.getEntryId(), e);
        }
        System.out.println("[SecondDawnRP] Loaded " + entries.size() + " trigger blocks.");
    }

    public void saveAll() {
        repository.save(entries.values());
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        serverTick++;

        for (TriggerEntry entry : entries.values()) {
            if (!entry.isArmed()) continue;
            if (entry.getTriggerMode() != TriggerMode.RADIUS) continue;

            ServerWorld world = resolveWorld(server, entry.getWorldKey());
            if (world == null) continue;

            BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
            double radius = entry.getRadiusBlocks();
            Set<UUID> wasIn = playersInRadius.getOrDefault(entry.getEntryId(), new HashSet<>());
            Set<UUID> nowIn = new HashSet<>();

            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!player.getBlockPos().isWithinDistance(pos, radius)) continue;
                nowIn.add(player.getUuid());

                if (wasIn.contains(player.getUuid())) continue;
                attemptFire(entry, player, server);
            }

            playersInRadius.put(entry.getEntryId(), nowIn);
        }
    }

    public void fireInteract(TriggerEntry entry, ServerPlayerEntity player) {
        if (!entry.isArmed()) return;
        if (entry.getTriggerMode() != TriggerMode.INTERACT) return;
        attemptFire(entry, player, player.getServer());
    }

    private void attemptFire(TriggerEntry entry, ServerPlayerEntity player, MinecraftServer server) {
        String cooldownKey = entry.getEntryId() + ":" + player.getUuid();

        long expiry = cooldownExpiry.getOrDefault(cooldownKey, 0L);
        if (serverTick < expiry) return;

        if (entry.getFireMode() == TriggerFireMode.FIRST_ENTRY) {
            if (entry.isFirstEntryFired()) return;
            entry.setFirstEntryFired(true);
            repository.save(entries.values());
        }

        cooldownExpiry.put(cooldownKey, serverTick + entry.getCooldownTicks());
        executeActions(entry, player, server);
    }

    // ── Action execution ──────────────────────────────────────────────────────

    private void executeActions(TriggerEntry entry, ServerPlayerEntity trigger, MinecraftServer server) {
        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());

        for (TriggerAction action : entry.getActions()) {
            switch (action.getType()) {
                case BROADCAST -> executeBroadcast(action.getPayload(), trigger, server);
                case ACTIVATE_LINKED -> executeToggleLinked(action.getPayload(), ToggleMode.ACTIVATE);
                case DEACTIVATE_LINKED -> executeToggleLinked(action.getPayload(), ToggleMode.DEACTIVATE);
                case TOGGLE_LINKED -> executeToggleLinked(action.getPayload(), ToggleMode.TOGGLE);
                case GENERATE_TASK -> executeGenerateTask(action.getPayload());
                case NOTIFY_GM -> executeNotifyGm(action.getPayload(), trigger, pos, server);
                case PLAY_SOUND -> executePlaySound(action.getPayload(), pos, (ServerWorld) trigger.getWorld());
                case APPLY_MEDICAL_CONDITION -> executeApplyMedicalCondition(action.getPayload(), trigger, pos);
            }
        }
    }

    private void executeBroadcast(String payload, ServerPlayerEntity trigger, MinecraftServer server) {
        int sep = payload.indexOf(':');
        if (sep < 0) return;

        String channel = payload.substring(0, sep).trim().toUpperCase();
        String message = payload.substring(sep + 1).trim();

        Text msg = Text.literal("[Trigger] " + message).formatted(Formatting.YELLOW);

        switch (channel) {
            case "ALL" -> server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(msg, false));
            case "GM_ONLY" -> server.getPlayerManager().getPlayerList().stream()
                    .filter(p -> p.hasPermissionLevel(2)
                            || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use"))
                    .forEach(p -> p.sendMessage(msg, false));
            default -> {
                if (channel.startsWith("DIVISION:")) {
                    String divName = channel.substring(9);
                    try {
                        Division div = Division.valueOf(divName);
                        server.getPlayerManager().getPlayerList().stream()
                                .filter(p -> {
                                    var profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(p.getUuid());
                                    return profile != null && div.equals(profile.getDivision());
                                })
                                .forEach(p -> p.sendMessage(msg, false));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private enum ToggleMode {
        ACTIVATE,
        DEACTIVATE,
        TOGGLE
    }

    private void executeToggleLinked(String payload, ToggleMode mode) {
        if (payload == null || payload.isBlank()) return;

        for (String id : payload.split(",")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty()) continue;

            switch (mode) {
                case ACTIVATE -> SecondDawnRP.ENV_EFFECT_SERVICE.toggle(trimmed, true);
                case DEACTIVATE -> SecondDawnRP.ENV_EFFECT_SERVICE.toggle(trimmed, false);
                case TOGGLE -> {
                    var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(trimmed);
                    opt.ifPresent(entry ->
                            SecondDawnRP.ENV_EFFECT_SERVICE.toggle(trimmed, !entry.isActive()));
                }
            }
        }
    }

    private void executeGenerateTask(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) return;

        String displayName = parts[0].trim();
        String description = parts[1].trim();
        String divName = parts[2].trim();

        try {
            Division div = Division.valueOf(divName.toUpperCase());
            SecondDawnRP.TASK_SERVICE.createPoolTask(
                    "trigger_task_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFFL),
                    displayName,
                    description,
                    div,
                    TaskObjectiveType.MANUAL_CONFIRM,
                    "trigger",
                    1,
                    25,
                    false,
                    null
            );
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void executeNotifyGm(String payload, ServerPlayerEntity trigger,
                                 BlockPos pos, MinecraftServer server) {
        Text msg = Text.literal("[TRIGGER] " + payload
                        + " | Player: " + trigger.getName().getString()
                        + " | Pos: " + pos.getX() + "," + pos.getY() + "," + pos.getZ())
                .formatted(Formatting.RED);

        server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.hasPermissionLevel(2)
                        || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use"))
                .forEach(p -> p.sendMessage(msg, false));
    }

    private void executePlaySound(String payload, BlockPos pos, ServerWorld world) {
        String[] parts = payload.split(":");
        if (parts.length < 2) return;

        String ns = parts[0];
        String path = parts[1];
        float volume = parts.length > 2 ? parseFloat(parts[2], 1.0f) : 1.0f;
        float pitch = parts.length > 3 ? parseFloat(parts[3], 1.0f) : 1.0f;

        SoundEvent sound = Registries.SOUND_EVENT.get(Identifier.of(ns, path));
        if (sound == null) return;

        world.playSound(null, pos, sound, SoundCategory.BLOCKS, volume, pitch);
    }

    private void executeApplyMedicalCondition(String payload,
                                              ServerPlayerEntity trigger,
                                              BlockPos pos) {
        if (payload == null || payload.isBlank()) return;
        if (SecondDawnRP.MEDICAL_SERVICE == null) return;
        if (SecondDawnRP.MEDICAL_CONDITION_REGISTRY == null) return;

        String[] parts = payload.split("\\|", 2);
        String conditionKey = parts[0].trim();
        String note = parts.length > 1 && !parts[1].trim().isBlank()
                ? parts[1].trim()
                : "Trigger hazard at " + pos.getX() + "," + pos.getY() + "," + pos.getZ();

        if (!SecondDawnRP.MEDICAL_CONDITION_REGISTRY.exists(conditionKey)) {
            return;
        }

        boolean alreadyActive = SecondDawnRP.MEDICAL_SERVICE.getActiveConditions(trigger.getUuid()).stream()
                .map(detail -> detail.condition().getConditionKey())
                .anyMatch(conditionKey::equals);

        if (alreadyActive) return;

        String appliedBy = "trigger";
        SecondDawnRP.MEDICAL_SERVICE.applyCondition(
                trigger.getUuid(),
                conditionKey,
                null,
                null,
                note,
                appliedBy
        );
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public TriggerEntry register(String worldKey, long blockPosLong, UUID registeredBy) {
        boolean exists = entries.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey) && e.getBlockPosLong() == blockPosLong);
        if (exists) throw new IllegalStateException("Already registered at this position.");

        String id = "trg_" + Long.toHexString(blockPosLong & 0xFFFFFFL)
                + "_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFL);
        TriggerEntry entry = new TriggerEntry(id, worldKey, blockPosLong, registeredBy);
        entries.put(id, entry);
        repository.save(entries.values());
        return entry;
    }

    public boolean unregister(String worldKey, long blockPosLong) {
        Optional<TriggerEntry> opt = entries.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey) && e.getBlockPosLong() == blockPosLong)
                .findFirst();
        if (opt.isEmpty()) return false;
        entries.remove(opt.get().getEntryId());
        repository.save(entries.values());
        return true;
    }

    public boolean setArmed(String entryId, boolean armed) {
        TriggerEntry e = entries.get(entryId);
        if (e == null) return false;
        e.setArmed(armed);
        if (!armed) e.setFirstEntryFired(false);
        repository.save(entries.values());
        return true;
    }

    public boolean resetFirstEntry(String entryId) {
        TriggerEntry e = entries.get(entryId);
        if (e == null) return false;
        e.setFirstEntryFired(false);
        repository.save(entries.values());
        return true;
    }

    public void saveEntry(TriggerEntry entry) {
        entries.put(entry.getEntryId(), entry);
        repository.save(entries.values());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Collection<TriggerEntry> getAll() {
        return entries.values();
    }

    public Optional<TriggerEntry> getById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Optional<TriggerEntry> getByPosition(String wk, long pos) {
        return entries.values().stream()
                .filter(e -> e.getWorldKey().equals(wk) && e.getBlockPosLong() == pos)
                .findFirst();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerWorld resolveWorld(MinecraftServer server, String worldKey) {
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(worldKey)) return w;
        }
        return null;
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}