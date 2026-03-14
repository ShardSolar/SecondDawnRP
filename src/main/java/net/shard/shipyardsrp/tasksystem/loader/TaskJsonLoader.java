package net.shard.shipyardsrp.tasksystem.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.shard.shipyardsrp.starfleetarchives.Division;
import net.shard.shipyardsrp.tasksystem.data.TaskObjectiveType;
import net.shard.shipyardsrp.tasksystem.data.TaskTemplate;
import net.shard.shipyardsrp.tasksystem.registry.TaskRegistry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TaskJsonLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load(ResourceManager resourceManager) {

        TaskRegistry.clear();

        Map<Identifier, Resource> resources =
                resourceManager.findResources("tasks", id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {

            try (InputStreamReader reader =
                         new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {

                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                TaskTemplate template = parseTask(json);

                TaskRegistry.register(template);

            } catch (Exception e) {

                System.err.println("[ShipyardsRP] Failed to load task file: " + entry.getKey());
                e.printStackTrace();

            }
        }

        System.out.println("[ShipyardsRP] Loaded " + TaskRegistry.getAll().size() + " tasks.");
    }

    private static TaskTemplate parseTask(JsonObject json) {

        String id = json.get("id").getAsString();
        String displayName = json.get("displayName").getAsString();
        String description = json.get("description").getAsString();

        Division division = Division.valueOf(json.get("division").getAsString());

        TaskObjectiveType objectiveType =
                TaskObjectiveType.valueOf(json.get("objectiveType").getAsString());

        String targetId = json.get("targetId").getAsString();

        int requiredAmount = json.get("requiredAmount").getAsInt();
        int rewardPoints = json.get("rewardPoints").getAsInt();

        boolean officerConfirmationRequired =
                json.get("officerConfirmationRequired").getAsBoolean();

        return new TaskTemplate(
                id,
                displayName,
                description,
                division,
                objectiveType,
                targetId,
                requiredAmount,
                rewardPoints,
                officerConfirmationRequired
        );
    }
}