package net.shard.seconddawnrp.degradation.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime configuration for the engineering degradation system.
 *
 * <p>Loaded from {@code config/assets/seconddawnrp/degradation_config.json}
 * on server start. All durations are in real-time milliseconds.
 *
 * <p>All fields are immutable except {@code degradationDisabled}, which can be
 * toggled at runtime via {@code /engineering degradation disable|enable} and
 * persisted immediately without a server restart.
 */
public class DegradationConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "degradation_config.json";

    // ── Immutable fields ──────────────────────────────────────────────────────

    /** Milliseconds between each health drain tick per component. Default: 5 minutes. */
    private final long drainIntervalMs;

    /** Health points drained per tick at NOMINAL status. */
    private final int drainPerTickNominal;

    /** Health points drained per tick at DEGRADED status (accelerated). */
    private final int drainPerTickDegraded;

    /** Health points drained per tick at CRITICAL status (further accelerated). */
    private final int drainPerTickCritical;

    /**
     * Minimum milliseconds between auto-generated repair tasks for the same
     * component. Prevents task spam when a component sits at CRITICAL for
     * a long time. Default: 30 minutes.
     */
    private final long taskGenerationCooldownMs;

    /**
     * Health restored per repair interaction (player uses Engineering PAD
     * on the block with an active repair task for that component).
     */
    private final int healthPerRepair;

    /** How many blocks away players receive particle warning pulses. */
    private final int warningRadiusBlocks;

    /**
     * Server ticks between DEGRADED warning pulses broadcast to nearby players.
     * Default: 1200 ticks (60 seconds).
     */
    private final int warningPulseTicksDegraded;

    /**
     * Server ticks between CRITICAL warning pulses.
     * Default: 400 ticks (20 seconds).
     */
    private final int warningPulseTicksCritical;

    private final String defaultRepairItemId;
    private final int    defaultRepairItemCount;

    // ── Mutable runtime field ─────────────────────────────────────────────────

    /**
     * When true, all component drain ticks are skipped server-wide.
     * Toggled at runtime via DegradationService.setDegradationGloballyDisabled().
     * Persisted immediately to disk via save() — survives server restart.
     * JSON key: "degradationDisabled". Defaults to false (missing key = not disabled).
     */
    private boolean degradationDisabled;

    /**
     * Path to the config file on disk. Stored at construction time so save()
     * can write back without needing a repository reference.
     * Null in the defaults() factory — save() is a no-op in that case.
     */
    private final Path filePath;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DegradationConfig(
            long drainIntervalMs,
            int drainPerTickNominal,
            int drainPerTickDegraded,
            int drainPerTickCritical,
            long taskGenerationCooldownMs,
            int healthPerRepair,
            int warningRadiusBlocks,
            int warningPulseTicksDegraded,
            int warningPulseTicksCritical,
            String defaultRepairItemId,
            int defaultRepairItemCount,
            boolean degradationDisabled,
            Path filePath) {
        this.drainIntervalMs           = drainIntervalMs;
        this.drainPerTickNominal       = drainPerTickNominal;
        this.drainPerTickDegraded      = drainPerTickDegraded;
        this.drainPerTickCritical      = drainPerTickCritical;
        this.taskGenerationCooldownMs  = taskGenerationCooldownMs;
        this.healthPerRepair           = healthPerRepair;
        this.warningRadiusBlocks       = warningRadiusBlocks;
        this.warningPulseTicksDegraded = warningPulseTicksDegraded;
        this.warningPulseTicksCritical = warningPulseTicksCritical;
        this.defaultRepairItemId       = defaultRepairItemId != null
                ? defaultRepairItemId : "minecraft:iron_ingot";
        this.defaultRepairItemCount    = defaultRepairItemCount > 0
                ? defaultRepairItemCount : 1;
        this.degradationDisabled       = degradationDisabled;
        this.filePath                  = filePath;
    }

    /**
     * Legacy constructor without degradationDisabled and filePath.
     * Used by existing call sites in DegradationConfigRepository.load() that
     * haven't been updated yet — they pass the two new args via the full
     * constructor, but this bridges any other instantiation sites.
     * degradationDisabled defaults to false; filePath defaults to null (no save).
     */
    public DegradationConfig(
            long drainIntervalMs,
            int drainPerTickNominal,
            int drainPerTickDegraded,
            int drainPerTickCritical,
            long taskGenerationCooldownMs,
            int healthPerRepair,
            int warningRadiusBlocks,
            int warningPulseTicksDegraded,
            int warningPulseTicksCritical,
            String defaultRepairItemId,
            int defaultRepairItemCount) {
        this(drainIntervalMs, drainPerTickNominal, drainPerTickDegraded,
                drainPerTickCritical, taskGenerationCooldownMs, healthPerRepair,
                warningRadiusBlocks, warningPulseTicksDegraded, warningPulseTicksCritical,
                defaultRepairItemId, defaultRepairItemCount,
                false, null);
    }

    // ── defaults() factory ────────────────────────────────────────────────────

    public static DegradationConfig defaults() {
        return new DegradationConfig(
                5 * 60 * 1000L,         // drainIntervalMs — 5 min
                1,                      // drainPerTickNominal
                2,                      // drainPerTickDegraded
                3,                      // drainPerTickCritical
                30 * 60 * 1000L,        // taskGenerationCooldownMs — 30 min
                20,                     // healthPerRepair
                16,                     // warningRadiusBlocks
                1200,                   // warningPulseTicksDegraded — 60s
                400,                    // warningPulseTicksCritical — 20s
                "minecraft:iron_ingot", // defaultRepairItemId
                1                       // defaultRepairItemCount
                // degradationDisabled = false, filePath = null
        );
    }

    // ── Mutable degradationDisabled ───────────────────────────────────────────

    /**
     * Returns true if degradation is globally paused.
     * Read by DegradationService.reload() to restore state after restart.
     */
    public boolean isDegradationDisabled() {
        return degradationDisabled;
    }

    /**
     * Toggle the global degradation disable flag.
     * Called by DegradationService.setDegradationGloballyDisabled() followed
     * immediately by save().
     */
    public void setDegradationDisabled(boolean disabled) {
        this.degradationDisabled = disabled;
    }

    /**
     * Write the current config state (including degradationDisabled) to disk.
     * Safe to call at any time — uses the filePath stored at construction.
     * No-op if filePath is null (defaults() instance or test instance).
     *
     * IOException is caught and logged rather than thrown — a failed save of
     * the disable flag is not fatal to server operation.
     */
    public void save() {
        if (filePath == null) {
            System.err.println("[SecondDawnRP] DegradationConfig.save() called but no filePath " +
                    "configured — degradationDisabled state will not persist.");
            return;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("drainIntervalMs",           drainIntervalMs);
        obj.addProperty("drainPerTickNominal",        drainPerTickNominal);
        obj.addProperty("drainPerTickDegraded",       drainPerTickDegraded);
        obj.addProperty("drainPerTickCritical",       drainPerTickCritical);
        obj.addProperty("taskGenerationCooldownMs",   taskGenerationCooldownMs);
        obj.addProperty("healthPerRepair",            healthPerRepair);
        obj.addProperty("warningRadiusBlocks",        warningRadiusBlocks);
        obj.addProperty("warningPulseTicksDegraded",  warningPulseTicksDegraded);
        obj.addProperty("warningPulseTicksCritical",  warningPulseTicksCritical);
        obj.addProperty("defaultRepairItemId",        defaultRepairItemId);
        obj.addProperty("defaultRepairItemCount",     defaultRepairItemCount);
        obj.addProperty("degradationDisabled",        degradationDisabled);

        try {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath)) {
                GSON.toJson(obj, w);
            }
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to save DegradationConfig: " + e.getMessage());
        }
    }

    // ── Immutable accessors ───────────────────────────────────────────────────

    public long   getDrainIntervalMs()           { return drainIntervalMs; }
    public int    getDrainPerTickNominal()        { return drainPerTickNominal; }
    public int    getDrainPerTickDegraded()       { return drainPerTickDegraded; }
    public int    getDrainPerTickCritical()       { return drainPerTickCritical; }
    public long   getTaskGenerationCooldownMs()   { return taskGenerationCooldownMs; }
    public int    getHealthPerRepair()            { return healthPerRepair; }
    public int    getWarningRadiusBlocks()        { return warningRadiusBlocks; }
    public int    getWarningPulseTicksDegraded()  { return warningPulseTicksDegraded; }
    public int    getWarningPulseTicksCritical()  { return warningPulseTicksCritical; }
    public String getDefaultRepairItemId()        { return defaultRepairItemId; }
    public int    getDefaultRepairItemCount()     { return defaultRepairItemCount; }
}