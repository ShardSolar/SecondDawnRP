package net.shard.seconddawnrp.tasksystem.terminal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonTaskTerminalRepository implements TaskTerminalRepository {

    private final Path filePath;
    private final Gson gson;

    public JsonTaskTerminalRepository(Path configDir) {
        this.filePath = configDir
                .resolve("assets")
                .resolve("seconddawnrp")
                .resolve("task_terminals.json");

        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws IOException {
        Files.createDirectories(filePath.getParent());

        if (!Files.exists(filePath)) {
            Files.writeString(filePath, "[]");
        }
    }

    @Override
    public List<TaskTerminalEntry> loadAll() {
        try {
            if (!Files.exists(filePath)) {
                return new ArrayList<>();
            }

            String json = Files.readString(filePath);
            TaskTerminalEntry[] entries = gson.fromJson(json, TaskTerminalEntry[].class);
            if (entries == null) {
                return new ArrayList<>();
            }

            return new ArrayList<>(List.of(entries));
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(List<TaskTerminalEntry> entries) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, gson.toJson(entries));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}