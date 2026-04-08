package net.shard.seconddawnrp.dimension;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads LocationDefinitions from data/seconddawnrp/dimensions/*.json at startup.
 *
 * Static registration — adding a dimension requires a JSON file and restart.
 * GMs manage activation state at runtime via LocationService.
 */
public class LocationRegistry {

    private static final Gson GSON = new Gson();
    private static final String DIMENSIONS_DIR = "data/seconddawnrp/dimensions";

    private final Map<String, LocationDefinition> registry = new LinkedHashMap<>();

    public void reload() {
        registry.clear();
        Path dir = Path.of(DIMENSIONS_DIR);

        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                writeExampleFile(dir);
            } catch (IOException e) {
                System.err.println("[SecondDawnRP] Failed to create dimensions directory: "
                        + e.getMessage());
            }
            System.out.println("[SecondDawnRP] LocationRegistry: no dimensions registered.");
            return;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadFile);
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to list dimensions directory: "
                    + e.getMessage());
        }

        System.out.println("[SecondDawnRP] LocationRegistry: loaded "
                + registry.size() + " dimension(s).");
    }

    private void loadFile(Path file) {
        try (InputStream in = Files.newInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            JsonObject obj = GSON.fromJson(reader, JsonObject.class);

            String dimensionId   = obj.get("dimensionId").getAsString();
            String displayName   = obj.get("displayName").getAsString();
            String description   = obj.has("description")
                    ? obj.get("description").getAsString() : "";
            double entryX        = obj.has("defaultEntryX")
                    ? obj.get("defaultEntryX").getAsDouble() : 0.5;
            double entryY        = obj.has("defaultEntryY")
                    ? obj.get("defaultEntryY").getAsDouble() : 64.0;
            double entryZ        = obj.has("defaultEntryZ")
                    ? obj.get("defaultEntryZ").getAsDouble() : 0.5;
            boolean taskIsolated = obj.has("taskPoolIsolated")
                    && obj.get("taskPoolIsolated").getAsBoolean();
            boolean proxReq      = obj.has("proximityRequired")
                    && obj.get("proximityRequired").getAsBoolean();

            // Optional orbital zone block — Phase 12 ship movement
            OrbitalZone orbitalZone = null;
            if (obj.has("orbitalZone") && !obj.get("orbitalZone").isJsonNull()) {
                var oz = obj.getAsJsonObject("orbitalZone");
                orbitalZone = new OrbitalZone(
                        oz.has("centerX") ? oz.get("centerX").getAsDouble() : 0,
                        oz.has("centerZ") ? oz.get("centerZ").getAsDouble() : 0,
                        oz.has("radius")  ? oz.get("radius").getAsDouble()  : 100,
                        oz.has("minimumWarpSpeed") ? oz.get("minimumWarpSpeed").getAsInt() : 0
                );
            }

            LocationDefinition def = new LocationDefinition(
                    dimensionId, displayName, description,
                    entryX, entryY, entryZ,
                    taskIsolated, proxReq, orbitalZone);

            registry.put(dimensionId, def);

        } catch (Exception e) {
            System.err.println("[SecondDawnRP] Failed to load dimension file "
                    + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void writeExampleFile(Path dir) throws IOException {
        String example = """
                {
                  "dimensionId": "colony_alpha",
                  "displayName": "Colony Alpha",
                  "description": "A temperate Class-M colony world. Breathable atmosphere, moderate gravity.",
                  "defaultEntryX": 0.5,
                  "defaultEntryY": 64.0,
                  "defaultEntryZ": 0.5,
                  "taskPoolIsolated": true,
                  "proximityRequired": false
                }
                """;
        Files.writeString(dir.resolve("colony_alpha.json"), example);
        System.out.println("[SecondDawnRP] Wrote example dimension file: colony_alpha.json");
    }

    public Optional<LocationDefinition> get(String dimensionId) {
        return Optional.ofNullable(registry.get(dimensionId));
    }

    public List<LocationDefinition> getAll() {
        return List.copyOf(registry.values());
    }

    public boolean exists(String dimensionId) {
        return registry.containsKey(dimensionId);
    }
}