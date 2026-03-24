package net.shard.seconddawnrp.gmevent.service;

import com.google.gson.*;
import net.shard.seconddawnrp.gmevent.data.MedicalConditionDefinition;
import net.shard.seconddawnrp.gmevent.data.VanillaEffectDefinition;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Loads and serves the vanilla effects and medical conditions registries.
 *
 * <p>Both registries are JSON files in {@code config/assets/seconddawnrp/}.
 * They ship with defaults and are admin-extensible — no code change needed
 * to add new entries.
 *
 * <p>Loaded once on server start via {@link #reload()}.
 * Read-only at runtime — changes require a server restart.
 */
public class GmRegistryService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private List<VanillaEffectDefinition> vanillaEffects = new ArrayList<>();
    private List<MedicalConditionDefinition> medicalConditions = new ArrayList<>();

    public GmRegistryService(Path configDir) {
        this.configDir = configDir;
    }

    public void reload() {
        vanillaEffects    = loadVanillaEffects();
        medicalConditions = loadMedicalConditions();
        System.out.println("[SecondDawnRP] GM Registry: loaded "
                + vanillaEffects.size() + " effects, "
                + medicalConditions.size() + " conditions.");
    }

    public List<VanillaEffectDefinition> getVanillaEffects()       { return vanillaEffects; }
    public List<MedicalConditionDefinition> getMedicalConditions()  { return medicalConditions; }

    // ── Vanilla effects ───────────────────────────────────────────────────────

    private List<VanillaEffectDefinition> loadVanillaEffects() {
        Path path = configDir.resolve("assets/seconddawnrp/vanilla_effects_registry.json");
        if (!Files.exists(path)) {
            writeDefaults(path, defaultVanillaEffectsJson());
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            List<VanillaEffectDefinition> list = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                list.add(new VanillaEffectDefinition(
                        o.get("effectId").getAsString(),
                        o.get("displayName").getAsString(),
                        o.has("defaultAmplitude") ? o.get("defaultAmplitude").getAsInt() : 0,
                        o.has("defaultDurationTicks") ? o.get("defaultDurationTicks").getAsInt() : 200));
            }
            return list;
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to load vanilla_effects_registry.json: " + e.getMessage());
            return defaultVanillaEffects();
        }
    }

    private List<MedicalConditionDefinition> loadMedicalConditions() {
        Path path = configDir.resolve("assets/seconddawnrp/medical_conditions_registry.json");
        if (!Files.exists(path)) {
            writeDefaults(path, defaultMedicalConditionsJson());
        }
        try (Reader r = Files.newBufferedReader(path)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            List<MedicalConditionDefinition> list = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                list.add(new MedicalConditionDefinition(
                        o.get("conditionId").getAsString(),
                        o.get("displayName").getAsString(),
                        o.has("defaultSeverity") ? o.get("defaultSeverity").getAsString() : "Moderate",
                        o.has("description") ? o.get("description").getAsString() : ""));
            }
            return list;
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to load medical_conditions_registry.json: " + e.getMessage());
            return defaultMedicalConditions();
        }
    }

    private void writeDefaults(Path path, String json) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json);
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to write defaults to " + path + ": " + e.getMessage());
        }
    }

    // ── Default data ──────────────────────────────────────────────────────────

    private static String defaultVanillaEffectsJson() {
        return """
[
  { "effectId": "minecraft:slowness",        "displayName": "Slowness",         "defaultAmplitude": 1, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:weakness",        "displayName": "Weakness",         "defaultAmplitude": 1, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:poison",          "displayName": "Poison",           "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:nausea",          "displayName": "Nausea",           "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:blindness",       "displayName": "Blindness",        "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:wither",          "displayName": "Wither",           "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:mining_fatigue",  "displayName": "Mining Fatigue",   "defaultAmplitude": 1, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:hunger",          "displayName": "Hunger",           "defaultAmplitude": 1, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:levitation",      "displayName": "Levitation",       "defaultAmplitude": 0, "defaultDurationTicks": 100 },
  { "effectId": "minecraft:darkness",        "displayName": "Darkness",         "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:strength",        "displayName": "Strength",         "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:speed",           "displayName": "Speed",            "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:resistance",      "displayName": "Resistance",       "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:fire_resistance", "displayName": "Fire Resistance",  "defaultAmplitude": 0, "defaultDurationTicks": 200 },
  { "effectId": "minecraft:regeneration",    "displayName": "Regeneration",     "defaultAmplitude": 0, "defaultDurationTicks": 200 }
]
""";
    }

    private static String defaultMedicalConditionsJson() {
        return """
[
  { "conditionId": "radiation_sickness",   "displayName": "Radiation Sickness",    "defaultSeverity": "Moderate", "description": "Exposure to ionising radiation" },
  { "conditionId": "neural_toxin",         "displayName": "Neural Toxin",          "defaultSeverity": "Severe",   "description": "Neurotoxic compound exposure" },
  { "conditionId": "plasma_burns",         "displayName": "Plasma Burns",          "defaultSeverity": "Moderate", "description": "Thermal plasma exposure injuries" },
  { "conditionId": "oxygen_deprivation",   "displayName": "Oxygen Deprivation",    "defaultSeverity": "Minor",    "description": "Atmospheric oxygen below safe levels" },
  { "conditionId": "pathogen_exposure",    "displayName": "Pathogen Exposure",     "defaultSeverity": "Moderate", "description": "Biological contaminant exposure" },
  { "conditionId": "chemical_burn",        "displayName": "Chemical Burn",         "defaultSeverity": "Minor",    "description": "Corrosive substance contact" },
  { "conditionId": "field_flux_syndrome",  "displayName": "Field Flux Syndrome",   "defaultSeverity": "Moderate", "description": "Prolonged energy field exposure" },
  { "conditionId": "sonic_trauma",         "displayName": "Sonic Trauma",          "defaultSeverity": "Minor",    "description": "High-frequency acoustic damage" },
  { "conditionId": "pressure_sickness",   "displayName": "Pressure Sickness",     "defaultSeverity": "Minor",    "description": "Rapid pressure change effects" },
  { "conditionId": "contamination",        "displayName": "Contamination",         "defaultSeverity": "Moderate", "description": "General hazardous material exposure" }
]
""";
    }

    private static List<VanillaEffectDefinition> defaultVanillaEffects() {
        return List.of(
                new VanillaEffectDefinition("minecraft:slowness",  "Slowness",  1, 200),
                new VanillaEffectDefinition("minecraft:weakness",  "Weakness",  1, 200),
                new VanillaEffectDefinition("minecraft:poison",    "Poison",    0, 200),
                new VanillaEffectDefinition("minecraft:nausea",    "Nausea",    0, 200),
                new VanillaEffectDefinition("minecraft:blindness", "Blindness", 0, 200)
        );
    }

    private static List<MedicalConditionDefinition> defaultMedicalConditions() {
        return List.of(
                new MedicalConditionDefinition("radiation_sickness", "Radiation Sickness", "Moderate", ""),
                new MedicalConditionDefinition("neural_toxin",       "Neural Toxin",       "Severe",   ""),
                new MedicalConditionDefinition("plasma_burns",       "Plasma Burns",       "Moderate", "")
        );
    }
}