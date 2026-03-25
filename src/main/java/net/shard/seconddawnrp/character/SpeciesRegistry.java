package net.shard.seconddawnrp.character;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads and caches all species definitions from JSON files.
 *
 * <p>Files live at:
 * {@code data/seconddawnrp/species/<id>.json}
 * relative to the server's working directory.
 *
 * <p>Loaded once on SERVER_STARTED via {@link #reload()}.
 * No hot-reload — restart the server to pick up new species files.
 *
 * <p>If the species directory does not exist, it is created automatically
 * and {@code human.json} is written as the default entry.
 */
public class SpeciesRegistry {

    private static final Gson GSON = new Gson();
    private static final String SPECIES_DIR = "data/seconddawnrp/species";

    /** Default human.json content written on first boot. */
    private static final String HUMAN_JSON = """
            {
              "id": "human",
              "displayName": "Human",
              "description": "Adaptable and resilient. Humanity has spread across the stars through determination and cooperation.",
              "startingLanguages": ["galactic_standard"]
            }
            """;

    private final Map<String, SpeciesDefinition> registry = new LinkedHashMap<>();

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Called on SERVER_STARTED. Clears and repopulates the registry from disk.
     */
    public void reload() {
        registry.clear();

        Path dir = Path.of(SPECIES_DIR);

        // Create directory + default human.json on first boot
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("human.json"), HUMAN_JSON);
            } catch (IOException e) {
                System.err.println("[SecondDawnRP] Failed to create species directory: " + e.getMessage());
            }
        }

        // Load every .json file in the directory
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadFile);
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to list species directory: " + e.getMessage());
        }

        System.out.println("[SecondDawnRP] Loaded " + registry.size() + " species.");
    }

    private void loadFile(Path file) {
        try (InputStream in = Files.newInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            String id          = obj.get("id").getAsString();
            String displayName = obj.get("displayName").getAsString();
            String description = obj.has("description")
                    ? obj.get("description").getAsString() : "";

            List<String> langs = new ArrayList<>();
            if (obj.has("startingLanguages")) {
                JsonArray arr = obj.getAsJsonArray("startingLanguages");
                for (JsonElement el : arr) langs.add(el.getAsString());
            }

            registry.put(id, new SpeciesDefinition(id, displayName, description, langs));
        } catch (Exception e) {
            System.err.println("[SecondDawnRP] Failed to load species file "
                    + file.getFileName() + ": " + e.getMessage());
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<SpeciesDefinition> get(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    /** Ordered list for display in the creation terminal dropdown. */
    public List<SpeciesDefinition> getAll() {
        return List.copyOf(registry.values());
    }

    public boolean exists(String id) {
        return registry.containsKey(id);
    }
}