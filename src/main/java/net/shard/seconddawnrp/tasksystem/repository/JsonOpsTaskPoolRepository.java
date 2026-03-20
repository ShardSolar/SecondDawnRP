package net.shard.seconddawnrp.tasksystem.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskPoolEntry;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonOpsTaskPoolRepository implements OpsTaskPoolRepository {

    private static final String FILE_NAME = "ops_task_pool.json";
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<OpsTaskPoolEntry>>() {}.getType();

    private final Path filePath;
    private final Gson gson;

    public JsonOpsTaskPoolRepository(Path configDir) {
        this.filePath = configDir.resolve(FILE_NAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws Exception {
        Files.createDirectories(filePath.getParent());

        if (!Files.exists(filePath)) {
            saveAll(List.of());
        }
    }

    @Override
    public List<OpsTaskPoolEntry> loadAll() {
        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<OpsTaskPoolEntry> entries = gson.fromJson(reader, ENTRY_LIST_TYPE);
            return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ops task pool from " + filePath, e);
        }
    }

    @Override
    public void saveAll(List<OpsTaskPoolEntry> entries) {
        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(entries, ENTRY_LIST_TYPE, writer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save ops task pool to " + filePath, e);
        }
    }
}