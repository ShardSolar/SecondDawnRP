package net.shard.seconddawnrp.terminal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * JSON-backed registry for terminal designations.
 * File: config/assets/seconddawnrp/terminal_designations.json
 *
 * V15: each entry now stores an optional "shipId" field.
 * Missing key on load defaults to null (backward-compatible with existing files).
 * Delete file on world wipe (positions are world-specific).
 */
public class TerminalDesignatorRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "terminal_designations.json";

    private final Path file;
    private final List<TerminalDesignatorEntry> entries = new ArrayList<>();

    public TerminalDesignatorRegistry(Path configDir) {
        this.file = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void init() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(file)) {
            Files.writeString(file, "[]", StandardCharsets.UTF_8);
        }
    }

    public void reload() {
        entries.clear();
        if (!Files.exists(file)) return;

        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isBlank() || raw.equals("[]")) return;

            JsonArray array = GSON.fromJson(raw, JsonArray.class);
            if (array == null) return;

            for (var element : array) {
                try {
                    JsonObject obj = element.getAsJsonObject();
                    String worldKey  = obj.get("worldKey").getAsString();
                    long   packedPos = obj.get("packedPos").getAsLong();
                    String typeName  = obj.get("type").getAsString();
                    // V15 — shipId is optional; null if key absent or explicitly null
                    String shipId = obj.has("shipId") && !obj.get("shipId").isJsonNull()
                            ? obj.get("shipId").getAsString() : null;

                    TerminalDesignatorType type = TerminalDesignatorType.valueOf(typeName);
                    entries.add(new TerminalDesignatorEntry(worldKey, packedPos, type, shipId));
                } catch (Exception ex) {
                    System.out.println("[SecondDawnRP] Skipping malformed terminal entry: "
                            + ex.getMessage());
                }
            }
            System.out.println("[SecondDawnRP] Loaded " + entries.size()
                    + " terminal designations.");
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to load terminal designations: "
                    + e.getMessage());
        }
    }

    public void saveAll() {
        JsonArray array = new JsonArray();
        for (TerminalDesignatorEntry entry : entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("worldKey",  entry.getWorldKey());
            obj.addProperty("packedPos", entry.getPackedPos());
            obj.addProperty("type",      entry.getType().name());
            // V15 — write shipId; null → JSON null (explicit, not omitted)
            if (entry.getShipId() != null) {
                obj.addProperty("shipId", entry.getShipId());
            } else {
                obj.add("shipId", com.google.gson.JsonNull.INSTANCE);
            }
            array.add(obj);
        }

        try {
            Files.writeString(file, GSON.toJson(array), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to save terminal designations: "
                    + e.getMessage());
        }
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Register or replace a terminal at the given position.
     * If a designation already exists at this position, it is overwritten.
     */
    public void register(String worldKey, BlockPos pos, TerminalDesignatorType type) {
        register(worldKey, pos, type, null);
    }

    /**
     * Register or replace a terminal at the given position with a ship binding.
     */
    public void register(String worldKey, BlockPos pos, TerminalDesignatorType type,
                         String shipId) {
        entries.removeIf(e -> e.matches(worldKey, pos));
        entries.add(new TerminalDesignatorEntry(worldKey, pos, type, shipId));
        saveAll();
    }

    /**
     * Remove the designation at this position.
     * @return true if something was removed.
     */
    public boolean remove(String worldKey, BlockPos pos) {
        boolean removed = entries.removeIf(e -> e.matches(worldKey, pos));
        if (removed) saveAll();
        return removed;
    }

    /**
     * Update the ship binding on an existing terminal in-place.
     * Replaces the entry with a new immutable instance carrying the new shipId.
     * @return true if the terminal was found and updated.
     */
    public boolean setShipId(String worldKey, BlockPos pos, String shipId) {
        for (int i = 0; i < entries.size(); i++) {
            TerminalDesignatorEntry e = entries.get(i);
            if (e.matches(worldKey, pos)) {
                entries.set(i, new TerminalDesignatorEntry(
                        e.getWorldKey(), e.getPackedPos(), e.getType(), shipId));
                saveAll();
                return true;
            }
        }
        return false;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<TerminalDesignatorEntry> get(String worldKey, BlockPos pos) {
        return entries.stream().filter(e -> e.matches(worldKey, pos)).findFirst();
    }

    public Optional<TerminalDesignatorEntry> get(String worldKey, long packedPos) {
        return entries.stream().filter(e -> e.matches(worldKey, packedPos)).findFirst();
    }

    public boolean isDesignated(String worldKey, BlockPos pos) {
        return entries.stream().anyMatch(e -> e.matches(worldKey, pos));
    }

    /**
     * All entries within a Chebyshev (cubic) radius of the given position.
     */
    public List<TerminalDesignatorEntry> getNearby(String worldKey, BlockPos center,
                                                   int radius) {
        List<TerminalDesignatorEntry> result = new ArrayList<>();
        for (TerminalDesignatorEntry entry : entries) {
            if (!entry.getWorldKey().equals(worldKey)) continue;
            BlockPos p = entry.getPos();
            if (Math.abs(p.getX() - center.getX()) <= radius
                    && Math.abs(p.getY() - center.getY()) <= radius
                    && Math.abs(p.getZ() - center.getZ()) <= radius) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<TerminalDesignatorEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }
}