package net.shard.shipyardsrp.tasksystem.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.shard.shipyardsrp.tasksystem.data.ActiveTask;
import net.shard.shipyardsrp.tasksystem.data.CompletedTaskRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JsonTaskStateRepository implements TaskStateRepository {

    private final Path taskStateDirectory;
    private final Gson gson;

    public JsonTaskStateRepository(Path configDir) {
        this.taskStateDirectory = configDir
                .resolve("assets")
                .resolve("shipyardsrp")
                .resolve("taskstate");

        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws IOException {
        Files.createDirectories(taskStateDirectory);
    }

    @Override
    public List<ActiveTask> loadActiveTasks(UUID playerUuid) {
        TaskStateSaveData data = loadSaveData(playerUuid);
        return new ArrayList<>(data.activeTasks);
    }

    @Override
    public List<CompletedTaskRecord> loadCompletedTasks(UUID playerUuid) {
        TaskStateSaveData data = loadSaveData(playerUuid);
        return new ArrayList<>(data.completedTasks);
    }

    @Override
    public void saveActiveTasks(UUID playerUuid, List<ActiveTask> activeTasks) {
        TaskStateSaveData data = loadSaveData(playerUuid);
        data.activeTasks = new ArrayList<>(activeTasks);
        writeSaveData(playerUuid, data);
    }

    @Override
    public void saveCompletedTasks(UUID playerUuid, List<CompletedTaskRecord> completedTasks) {
        TaskStateSaveData data = loadSaveData(playerUuid);
        data.completedTasks = new ArrayList<>(completedTasks);
        writeSaveData(playerUuid, data);
    }

    @Override
    public void clearPlayerTaskState(UUID playerUuid) {
        try {
            Files.deleteIfExists(getTaskStatePath(playerUuid));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TaskStateSaveData loadSaveData(UUID playerUuid) {
        Path path = getTaskStatePath(playerUuid);
        if (!Files.exists(path)) {
            return new TaskStateSaveData();
        }

        try {
            String json = Files.readString(path);
            TaskStateSaveData data = gson.fromJson(json, TaskStateSaveData.class);
            return data != null ? data : new TaskStateSaveData();
        } catch (Exception e) {
            e.printStackTrace();
            return new TaskStateSaveData();
        }
    }

    private void writeSaveData(UUID playerUuid, TaskStateSaveData data) {
        try {
            Files.createDirectories(taskStateDirectory);
            Files.writeString(getTaskStatePath(playerUuid), gson.toJson(data));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Path getTaskStatePath(UUID playerUuid) {
        return taskStateDirectory.resolve(playerUuid.toString() + ".json");
    }

    private static class TaskStateSaveData {
        private List<ActiveTask> activeTasks = new ArrayList<>();
        private List<CompletedTaskRecord> completedTasks = new ArrayList<>();
    }
}