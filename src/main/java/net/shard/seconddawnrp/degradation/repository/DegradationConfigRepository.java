package net.shard.seconddawnrp.degradation.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.shard.seconddawnrp.degradation.data.DegradationConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves the {@link DegradationConfig} from/to
 * {@code config/assets/seconddawnrp/degradation_config.json}.
 *
 * <p>If the file does not exist, defaults are written and returned.
 *
 * <p>The loaded {@link DegradationConfig} instance receives the file path at
 * construction so it can call {@code save()} directly when the runtime
 * {@code degradationDisabled} flag is toggled, without needing to route
 * back through this repository.
 */
public class DegradationConfigRepository {

    private static final String FILE_NAME = "degradation_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    public DegradationConfigRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            save(DegradationConfig.defaults());
        }
    }

    public DegradationConfig load() {
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj == null) return DegradationConfig.defaults();
            return new DegradationConfig(
                    getLong(obj,   "drainIntervalMs",           5 * 60 * 1000L),
                    getInt(obj,    "drainPerTickNominal",        1),
                    getInt(obj,    "drainPerTickDegraded",       2),
                    getInt(obj,    "drainPerTickCritical",       3),
                    getLong(obj,   "taskGenerationCooldownMs",   30 * 60 * 1000L),
                    getInt(obj,    "healthPerRepair",            20),
                    getInt(obj,    "warningRadiusBlocks",        16),
                    getInt(obj,    "warningPulseTicksDegraded",  1200),
                    getInt(obj,    "warningPulseTicksCritical",  400),
                    getString(obj, "defaultRepairItemId",        "minecraft:iron_ingot"),
                    getInt(obj,    "defaultRepairItemCount",     1),
                    // New in V15 — defaults to false so existing configs are unaffected
                    getBool(obj,   "degradationDisabled",        false),
                    filePath
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + FILE_NAME, e);
        }
    }

    /**
     * Full save — writes all fields including degradationDisabled.
     * Called by init() on first run and by DegradationConfig.save() at runtime.
     * Keeping this method here as well allows external callers (e.g. tests,
     * admin commands) to save a fresh config without going through the instance.
     */
    public void save(DegradationConfig config) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("drainIntervalMs",          config.getDrainIntervalMs());
        obj.addProperty("drainPerTickNominal",       config.getDrainPerTickNominal());
        obj.addProperty("drainPerTickDegraded",      config.getDrainPerTickDegraded());
        obj.addProperty("drainPerTickCritical",      config.getDrainPerTickCritical());
        obj.addProperty("taskGenerationCooldownMs",  config.getTaskGenerationCooldownMs());
        obj.addProperty("healthPerRepair",           config.getHealthPerRepair());
        obj.addProperty("warningRadiusBlocks",       config.getWarningRadiusBlocks());
        obj.addProperty("warningPulseTicksDegraded", config.getWarningPulseTicksDegraded());
        obj.addProperty("warningPulseTicksCritical", config.getWarningPulseTicksCritical());
        obj.addProperty("defaultRepairItemId",       config.getDefaultRepairItemId());
        obj.addProperty("defaultRepairItemCount",    config.getDefaultRepairItemCount());
        obj.addProperty("degradationDisabled",       config.isDegradationDisabled());
        try (Writer w = Files.newBufferedWriter(filePath)) {
            GSON.toJson(obj, w);
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static long getLong(JsonObject obj, String key, long def) {
        return obj.has(key) ? obj.get(key).getAsLong() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }
}