package net.shard.seconddawnrp.degradation.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JSON-backed component repository.
 *
 * <p>Data is stored at
 * {@code config/assets/seconddawnrp/components.json}.
 * Writes are atomic: the new payload is written to a {@code .tmp} file
 * and then moved over the existing file, preventing partial writes on
 * server crash.
 *
 * <p>V15: {@code shipId} field added to serialization. Missing key on load
 * defaults to {@code null} (unowned/global) — existing component files are
 * backward-compatible with no manual migration required.
 */
public class JsonComponentRepository implements ComponentRepository {

    private static final String FILE_NAME = "components.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public JsonComponentRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath)) {
                w.write("[]");
            }
        }
    }

    @Override
    public void save(ComponentEntry entry) {
        List<ComponentEntry> all = new ArrayList<>(loadAll());
        all.removeIf(e -> e.getComponentId().equals(entry.getComponentId()));
        all.add(entry);
        writeAll(all);
    }

    @Override
    public void saveAll(Collection<ComponentEntry> entries) {
        writeAll(new ArrayList<>(entries));
    }

    @Override
    public Collection<ComponentEntry> loadAll() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return new ArrayList<>();
            List<ComponentEntry> result = new ArrayList<>();
            for (JsonElement el : arr) {
                result.add(fromJson(el.getAsJsonObject()));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load components.json", e);
        }
    }

    @Override
    public Optional<ComponentEntry> findById(String componentId) {
        return loadAll().stream()
                .filter(e -> e.getComponentId().equals(componentId))
                .findFirst();
    }

    @Override
    public Optional<ComponentEntry> findByPosition(String worldKey, long blockPosLong) {
        return loadAll().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
    }

    @Override
    public void delete(String componentId) {
        List<ComponentEntry> all = new ArrayList<>(loadAll());
        all.removeIf(e -> e.getComponentId().equals(componentId));
        writeAll(all);
    }

    @Override
    public void deleteAll() {
        writeAll(new ArrayList<>());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void writeAll(List<ComponentEntry> entries) {
        JsonArray arr = new JsonArray();
        for (ComponentEntry e : entries) {
            arr.add(toJson(e));
        }
        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            GSON.toJson(arr, w);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write components.tmp", e);
        }
        try {
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to atomically replace components.json", e);
        }
    }

    private static JsonObject toJson(ComponentEntry e) {
        JsonObject obj = new JsonObject();
        obj.addProperty("componentId",       e.getComponentId());
        obj.addProperty("worldKey",          e.getWorldKey());
        obj.addProperty("blockPosLong",      e.getBlockPosLong());
        obj.addProperty("blockTypeId",       e.getBlockTypeId());
        obj.addProperty("displayName",       e.getDisplayName());
        obj.addProperty("health",            e.getHealth());
        obj.addProperty("status",            e.getStatus().name());
        obj.addProperty("missingBlock",      e.isMissingBlock());
        obj.addProperty("lastDrainTickMs",   e.getLastDrainTickMs());
        obj.addProperty("lastTaskGeneratedMs", e.getLastTaskGeneratedMs());
        obj.addProperty("registeredByUuid",
                e.getRegisteredByUuid() != null ? e.getRegisteredByUuid().toString() : null);
        obj.addProperty("repairItemId",      e.getRepairItemId());
        obj.addProperty("repairItemCount",   e.getRepairItemCount());
        // V15 — null written as JSON null; absent on read defaults to null (backward compat)
        if (e.getShipId() != null) {
            obj.addProperty("shipId", e.getShipId());
        } else {
            obj.add("shipId", com.google.gson.JsonNull.INSTANCE);
        }
        return obj;
    }

    private static ComponentEntry fromJson(JsonObject obj) {
        String componentId       = obj.get("componentId").getAsString();
        String worldKey          = obj.get("worldKey").getAsString();
        long   blockPosLong      = obj.get("blockPosLong").getAsLong();
        String blockTypeId       = obj.get("blockTypeId").getAsString();
        String displayName       = obj.get("displayName").getAsString();
        int    health            = obj.get("health").getAsInt();
        ComponentStatus status   = ComponentStatus.valueOf(obj.get("status").getAsString());
        long   lastDrainTickMs   = obj.get("lastDrainTickMs").getAsLong();
        long   lastTaskGeneratedMs = obj.get("lastTaskGeneratedMs").getAsLong();

        String registeredByStr = obj.has("registeredByUuid")
                && !obj.get("registeredByUuid").isJsonNull()
                ? obj.get("registeredByUuid").getAsString() : null;
        UUID registeredByUuid = registeredByStr != null
                ? UUID.fromString(registeredByStr) : null;

        String repairItemId = obj.has("repairItemId") && !obj.get("repairItemId").isJsonNull()
                ? obj.get("repairItemId").getAsString() : null;
        int repairItemCount = obj.has("repairItemCount")
                ? obj.get("repairItemCount").getAsInt() : 0;

        boolean missingBlock = obj.has("missingBlock")
                && obj.get("missingBlock").getAsBoolean();

        // V15 — defaults to null if key absent (pre-V15 file) or explicitly null
        String shipId = obj.has("shipId") && !obj.get("shipId").isJsonNull()
                ? obj.get("shipId").getAsString() : null;

        return new ComponentEntry(
                componentId, worldKey, blockPosLong, blockTypeId, displayName,
                health, status, lastDrainTickMs, lastTaskGeneratedMs,
                registeredByUuid, repairItemId, repairItemCount, missingBlock,
                shipId
        );
    }
}