package net.shard.seconddawnrp.tasksystem.registry;

import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;

import java.util.*;

public final class TaskRegistry {

    private static final Map<String, TaskTemplate> TASKS = new LinkedHashMap<>();

    private TaskRegistry() {
    }

    public static void register(TaskTemplate template) {
        Objects.requireNonNull(template, "template");

        String id = template.getId();
        if (TASKS.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate task id: " + id);
        }

        TASKS.put(id, template);
    }

    public static TaskTemplate get(String id) {
        return TASKS.get(id);
    }

    public static Collection<TaskTemplate> getAll() {
        return Collections.unmodifiableCollection(TASKS.values());
    }

    public static List<TaskTemplate> getByDivision(Division division) {
        Objects.requireNonNull(division, "division");

        List<TaskTemplate> results = new ArrayList<>();
        for (TaskTemplate template : TASKS.values()) {
            if (template.getDivision() == division) {
                results.add(template);
            }
        }
        return Collections.unmodifiableList(results);
    }

    public static boolean contains(String id) {
        return TASKS.containsKey(id);
    }

    public static void clear() {
        TASKS.clear();
    }

    public static void bootstrap() {
    }
}