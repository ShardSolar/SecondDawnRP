package net.shard.shipyardsrp.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.shard.shipyardsrp.ShipyardsRP;
import net.shard.shipyardsrp.tasksystem.padd.TaskPaddItem;

public final class ModItems {

    public static final Item TASK_PADD = register(
            "task_padd",
            new TaskPaddItem(new Item.Settings().maxCount(1))
    );

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(
                Registries.ITEM,
                Identifier.of(ShipyardsRP.MOD_ID, name),
                item
        );
    }

    public static void register() {
        // static init hook
    }
}