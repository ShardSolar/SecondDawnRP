package net.shard.seconddawnrp.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.item.ComponentRegistrationTool;
import net.shard.seconddawnrp.degradation.item.EngineeringPadItem;
import net.shard.seconddawnrp.dice.item.RpPaddItem;
import net.shard.seconddawnrp.gmevent.item.*;
import net.shard.seconddawnrp.tasksystem.pad.OperationsPadItem;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadItem;
import net.shard.seconddawnrp.tasksystem.terminal.TaskTerminalToolItem;
import net.shard.seconddawnrp.warpcore.item.WarpCoreTool;

public class ModItems {

    public static final Item TASK_PAD = register("task_pad",
            new TaskPadItem(new Item.Settings().maxCount(1)));

    public static final Item OPERATIONS_PAD = register("operations_pad",
            new OperationsPadItem(new Item.Settings().maxCount(1)));

    public static final Item TASK_TERMINAL_TOOL = register(
            "task_terminal_tool",
            new TaskTerminalToolItem(new Item.Settings().maxCount(1))
    );

    public static final Item SPAWN_BLOCK_CONFIG_TOOL = register(
            "spawn_block_config_tool",
            new SpawnBlockConfigTool(new Item.Settings().maxCount(1))
    );

    public static final Item SPAWN_ITEM_TOOL = register(
            "spawn_item_tool",
            new SpawnItemTool(new Item.Settings().maxCount(1))
    );

    public static final Item ENGINEERING_PAD = register(
            "engineering_pad",
            new EngineeringPadItem(new Item.Settings().maxCount(1))
    );

    public static final Item COMPONENT_REGISTRATION_TOOL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "component_registration_tool"),
            new ComponentRegistrationTool(new Item.Settings().maxCount(1)));

    public static final Item WARP_CORE_TOOL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "warp_core_tool"),
            new WarpCoreTool(new Item.Settings().maxCount(1))
    );

    public static final Item FUEL_ROD = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "fuel_rod"),
            new Item(new Item.Settings().maxCount(64))
    );

    public static final Item CONTAINMENT_CELL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "containment_cell"),
            new Item(new Item.Settings().maxCount(16))
    );

    public static final Item RESONANCE_COIL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "resonance_coil"),
            new Item(new Item.Settings().maxCount(4))
    );

    public static final Item ENVIRONMENTAL_EFFECT_TOOL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "environmental_effect_tool"),
            new EnvironmentalEffectToolItem(new Item.Settings().maxCount(1))
    );

    public static final Item TRIGGER_TOOL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "trigger_tool"),
            new TriggerToolItem(new Item.Settings().maxCount(1))
    );

    public static final Item ANOMALY_MARKER_TOOL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "anomaly_marker_tool"),
            new AnomalyMarkerToolItem(new Item.Settings().maxCount(1))
    );

    public static final Item RP_PADD = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "rp_padd"),
            new RpPaddItem(new Item.Settings().maxCount(1))
    );

    public static final Item TERMINAL_DESIGNATOR_TOOL = Registry.register(
            Registries.ITEM,
            Identifier.of(SecondDawnRP.MOD_ID, "terminal_designator_tool"),
            new net.shard.seconddawnrp.terminal.TerminalDesignatorToolItem(
                    new Item.Settings().maxCount(1))
    );

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, SecondDawnRP.id(id), item);
    }

    public static void register() {
        // no-op, forces class load
    }
}