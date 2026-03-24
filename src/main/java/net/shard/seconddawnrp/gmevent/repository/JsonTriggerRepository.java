package net.shard.seconddawnrp.gmevent.repository;

import com.google.gson.*;
import net.shard.seconddawnrp.gmevent.data.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * JSON-backed repository for Trigger Block entries.
 * Stored at {@code config/assets/seconddawnrp/trigger_blocks.json}.
 */
public class JsonTriggerRepository {

    private static final String FILE_NAME = "trigger_blocks.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;

    public JsonTriggerRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath)) { w.write("[]"); }
        }
    }

    public void save(Collection<TriggerEntry> entries) {
        JsonArray arr = new JsonArray();
        for (TriggerEntry e : entries) arr.add(toJson(e));
        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(arr, w); }
        catch (IOException ex) { throw new RuntimeException("Failed to write trigger_blocks.tmp", ex); }
        try { Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ex) { throw new RuntimeException("Failed to replace trigger_blocks.json", ex); }
    }

    public List<TriggerEntry> loadAll() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return new ArrayList<>();
            List<TriggerEntry> result = new ArrayList<>();
            for (JsonElement el : arr) result.add(fromJson(el.getAsJsonObject()));
            return result;
        } catch (IOException e) { throw new RuntimeException("Failed to load trigger_blocks.json", e); }
    }

    private static JsonObject toJson(TriggerEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("entryId", e.getEntryId());
        o.addProperty("worldKey", e.getWorldKey());
        o.addProperty("blockPosLong", e.getBlockPosLong());
        o.addProperty("registeredByUuid", e.getRegisteredByUuid() != null ? e.getRegisteredByUuid().toString() : null);
        o.addProperty("triggerMode", e.getTriggerMode().name());
        o.addProperty("fireMode", e.getFireMode().name());
        o.addProperty("radiusBlocks", e.getRadiusBlocks());
        o.addProperty("cooldownTicks", e.getCooldownTicks());
        o.addProperty("armed", e.isArmed());
        o.addProperty("firstEntryFired", e.isFirstEntryFired());
        JsonArray actions = new JsonArray();
        for (TriggerAction a : e.getActions()) {
            JsonObject ao = new JsonObject();
            ao.addProperty("type", a.getType().name());
            ao.addProperty("payload", a.getPayload());
            actions.add(ao);
        }
        o.add("actions", actions);
        return o;
    }

    private static TriggerEntry fromJson(JsonObject o) {
        String uuidStr = o.has("registeredByUuid") && !o.get("registeredByUuid").isJsonNull()
                ? o.get("registeredByUuid").getAsString() : null;
        TriggerEntry e = new TriggerEntry(
                o.get("entryId").getAsString(),
                o.get("worldKey").getAsString(),
                o.get("blockPosLong").getAsLong(),
                uuidStr != null ? UUID.fromString(uuidStr) : null);
        if (o.has("triggerMode")) e.setTriggerMode(TriggerMode.valueOf(o.get("triggerMode").getAsString()));
        if (o.has("fireMode"))    e.setFireMode(TriggerFireMode.valueOf(o.get("fireMode").getAsString()));
        if (o.has("radiusBlocks"))   e.setRadiusBlocks(o.get("radiusBlocks").getAsInt());
        if (o.has("cooldownTicks"))  e.setCooldownTicks(o.get("cooldownTicks").getAsInt());
        if (o.has("armed"))          e.setArmed(o.get("armed").getAsBoolean());
        if (o.has("firstEntryFired")) e.setFirstEntryFired(o.get("firstEntryFired").getAsBoolean());
        List<TriggerAction> actions = new ArrayList<>();
        if (o.has("actions")) {
            for (JsonElement el : o.get("actions").getAsJsonArray()) {
                JsonObject ao = el.getAsJsonObject();
                actions.add(new TriggerAction(
                        TriggerActionType.valueOf(ao.get("type").getAsString()),
                        ao.get("payload").getAsString()));
            }
        }
        e.setActions(actions);
        return e;
    }
}