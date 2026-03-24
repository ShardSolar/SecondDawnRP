package net.shard.seconddawnrp.gmevent.repository;

import com.google.gson.*;
import net.shard.seconddawnrp.gmevent.data.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * JSON-backed repository for Environmental Effect Block entries.
 * Stored at {@code config/assets/seconddawnrp/env_effects.json}.
 * Atomic writes via .tmp swap.
 */
public class JsonEnvironmentalEffectRepository {

    private static final String FILE_NAME = "env_effects.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;

    public JsonEnvironmentalEffectRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath)) { w.write("[]"); }
        }
    }

    public void save(Collection<EnvironmentalEffectEntry> entries) {
        JsonArray arr = new JsonArray();
        for (EnvironmentalEffectEntry e : entries) arr.add(toJson(e));
        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(arr, w); }
        catch (IOException ex) { throw new RuntimeException("Failed to write env_effects.tmp", ex); }
        try { Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ex) { throw new RuntimeException("Failed to replace env_effects.json", ex); }
    }

    public List<EnvironmentalEffectEntry> loadAll() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return new ArrayList<>();
            List<EnvironmentalEffectEntry> result = new ArrayList<>();
            for (JsonElement el : arr) result.add(fromJson(el.getAsJsonObject()));
            return result;
        } catch (IOException e) { throw new RuntimeException("Failed to load env_effects.json", e); }
    }

    private static JsonObject toJson(EnvironmentalEffectEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("entryId", e.getEntryId());
        o.addProperty("worldKey", e.getWorldKey());
        o.addProperty("blockPosLong", e.getBlockPosLong());
        o.addProperty("registeredByUuid", e.getRegisteredByUuid() != null ? e.getRegisteredByUuid().toString() : null);
        JsonArray fx = new JsonArray();
        e.getVanillaEffects().forEach(fx::add);
        o.add("vanillaEffects", fx);
        o.addProperty("medicalConditionId", e.getMedicalConditionId());
        o.addProperty("medicalConditionSeverity", e.getMedicalConditionSeverity());
        o.addProperty("radiusBlocks", e.getRadiusBlocks());
        o.addProperty("lingerMode", e.getLingerMode().name());
        o.addProperty("lingerDurationTicks", e.getLingerDurationTicks());
        o.addProperty("fireMode", e.getFireMode().name());
        o.addProperty("onEntryCooldownTicks", e.getOnEntryCooldownTicks());
        o.addProperty("visibility", e.getVisibility().name());
        o.addProperty("active", e.isActive());
        return o;
    }

    private static EnvironmentalEffectEntry fromJson(JsonObject o) {
        String uuidStr = o.has("registeredByUuid") && !o.get("registeredByUuid").isJsonNull()
                ? o.get("registeredByUuid").getAsString() : null;
        EnvironmentalEffectEntry e = new EnvironmentalEffectEntry(
                o.get("entryId").getAsString(),
                o.get("worldKey").getAsString(),
                o.get("blockPosLong").getAsLong(),
                uuidStr != null ? UUID.fromString(uuidStr) : null);
        List<String> fx = new ArrayList<>();
        if (o.has("vanillaEffects")) o.get("vanillaEffects").getAsJsonArray().forEach(el -> fx.add(el.getAsString()));
        e.setVanillaEffects(fx);
        if (o.has("medicalConditionId") && !o.get("medicalConditionId").isJsonNull())
            e.setMedicalConditionId(o.get("medicalConditionId").getAsString());
        if (o.has("medicalConditionSeverity")) e.setMedicalConditionSeverity(o.get("medicalConditionSeverity").getAsString());
        if (o.has("radiusBlocks")) e.setRadiusBlocks(o.get("radiusBlocks").getAsInt());
        if (o.has("lingerMode")) e.setLingerMode(LingerMode.valueOf(o.get("lingerMode").getAsString()));
        if (o.has("lingerDurationTicks")) e.setLingerDurationTicks(o.get("lingerDurationTicks").getAsInt());
        if (o.has("fireMode")) e.setFireMode(EnvFireMode.valueOf(o.get("fireMode").getAsString()));
        if (o.has("onEntryCooldownTicks")) e.setOnEntryCooldownTicks(o.get("onEntryCooldownTicks").getAsInt());
        if (o.has("visibility")) e.setVisibility(EnvVisibility.valueOf(o.get("visibility").getAsString()));
        if (o.has("active")) e.setActive(o.get("active").getAsBoolean());
        return e;
    }
}