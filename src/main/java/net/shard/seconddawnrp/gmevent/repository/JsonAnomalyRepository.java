package net.shard.seconddawnrp.gmevent.repository;

import com.google.gson.*;
import net.shard.seconddawnrp.gmevent.data.AnomalyEntry;
import net.shard.seconddawnrp.gmevent.data.AnomalyType;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * JSON-backed repository for Anomaly Marker Block entries.
 * Stored at {@code config/assets/seconddawnrp/anomaly_markers.json}.
 *
 * <p>Active state is NOT persisted — always written as false.
 * Contacts must be reactivated each session.
 */
public class JsonAnomalyRepository {

    private static final String FILE_NAME = "anomaly_markers.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;

    public JsonAnomalyRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath)) { w.write("[]"); }
        }
    }

    public void save(Collection<AnomalyEntry> entries) {
        JsonArray arr = new JsonArray();
        for (AnomalyEntry e : entries) arr.add(toJson(e));
        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(arr, w); }
        catch (IOException ex) { throw new RuntimeException("Failed to write anomaly_markers.tmp", ex); }
        try { Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ex) { throw new RuntimeException("Failed to replace anomaly_markers.json", ex); }
    }

    public List<AnomalyEntry> loadAll() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return new ArrayList<>();
            List<AnomalyEntry> result = new ArrayList<>();
            for (JsonElement el : arr) result.add(fromJson(el.getAsJsonObject()));
            return result;
        } catch (IOException e) { throw new RuntimeException("Failed to load anomaly_markers.json", e); }
    }

    private static JsonObject toJson(AnomalyEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("entryId", e.getEntryId());
        o.addProperty("worldKey", e.getWorldKey());
        o.addProperty("blockPosLong", e.getBlockPosLong());
        o.addProperty("registeredByUuid",
                e.getRegisteredByUuid() != null ? e.getRegisteredByUuid().toString() : null);
        o.addProperty("name", e.getName());
        o.addProperty("type", e.getType().name());
        o.addProperty("description", e.getDescription());
        // active intentionally NOT persisted — always false on reload
        return o;
    }

    private static AnomalyEntry fromJson(JsonObject o) {
        String uuidStr = o.has("registeredByUuid") && !o.get("registeredByUuid").isJsonNull()
                ? o.get("registeredByUuid").getAsString() : null;
        AnomalyEntry e = new AnomalyEntry(
                o.get("entryId").getAsString(),
                o.get("worldKey").getAsString(),
                o.get("blockPosLong").getAsLong(),
                uuidStr != null ? UUID.fromString(uuidStr) : null,
                o.has("name") ? o.get("name").getAsString() : "Unknown Anomaly",
                o.has("type") ? AnomalyType.valueOf(o.get("type").getAsString()) : AnomalyType.UNKNOWN);
        if (o.has("description")) e.setDescription(o.get("description").getAsString());
        // active always starts false
        return e;
    }
}