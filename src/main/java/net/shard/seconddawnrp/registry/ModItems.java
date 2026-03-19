package net.shard.seconddawnrp.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tasksystem.pad.OperationsPadItem;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadItem;

public class ModItems {

    public static final Item TASK_PAD = register("task_pad",
            new TaskPadItem(new Item.Settings().maxCount(1)));

    public static final Item OPERATIONS_PAD = register("operations_pad",
            new OperationsPadItem(new Item.Settings().maxCount(1)));

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, SecondDawnRP.id(id), item);
    }

    public static void register() {
        // no-op, forces class load
    }
}