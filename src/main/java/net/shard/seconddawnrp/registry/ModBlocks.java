package net.shard.seconddawnrp.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.block.ConduitBlock;
import net.shard.seconddawnrp.warpcore.block.WarpCoreControllerBlock;
import net.shard.seconddawnrp.warpcore.block.WarpCoreControllerBlockEntity;

public final class ModBlocks {

    private ModBlocks() {}

    // ── Warp Core structural blocks ───────────────────────────────────────────

    public static final Block WARP_CORE_CASING = register("warp_core_casing",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_AQUA)
                    .strength(3.5f, 6.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool()));

    public static final Block WARP_CORE_INJECTOR = register("warp_core_injector",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_AQUA)
                    .strength(3.5f, 6.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool()));

    public static final Block WARP_CORE_COLUMN = register("warp_core_column",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.LIGHT_BLUE)
                    .strength(3.5f, 6.0f)
                    .sounds(BlockSoundGroup.GLASS)
                    .luminance(state -> 8)
                    .requiresTool()));

    public static final Block WARP_CORE_CONTROLLER = register("warp_core_controller",
            new WarpCoreControllerBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_AQUA)
                    .strength(3.5f, 6.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool()));

    // ── Ship infrastructure blocks ────────────────────────────────────────────

    public static final Block CONDUIT = register("conduit",
            new ConduitBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.GRAY)
                    .strength(2.0f, 4.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool()));

    public static final Block POWER_RELAY = register("power_relay",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.ORANGE)
                    .strength(2.0f, 4.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool()));

    public static final Block FUEL_TANK = register("fuel_tank",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.LIGHT_BLUE)
                    .strength(3.0f, 5.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool()));

    public static final Block CHARACTER_CREATION_TERMINAL = register("character_creation_terminal",
            new net.shard.seconddawnrp.character.CharacterCreationTerminalBlock(
                    AbstractBlock.Settings.create()
                            .mapColor(MapColor.CYAN)
                            .strength(3.0f, 6.0f)
                            .sounds(BlockSoundGroup.METAL)
                            .requiresTool()));

    // ── Block entity types ────────────────────────────────────────────────────

    public static BlockEntityType<WarpCoreControllerBlockEntity> WARP_CORE_CONTROLLER_ENTITY;
    public static BlockEntityType<net.shard.seconddawnrp.character.CharacterCreationTerminalBlock
            .CharacterCreationTerminalBlockEntity> CHARACTER_CREATION_TERMINAL_ENTITY;
    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        // Register block entity type — must happen AFTER WARP_CORE_CONTROLLER is created above
        WARP_CORE_CONTROLLER_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(SecondDawnRP.MOD_ID, "warp_core_controller"),
                BlockEntityType.Builder.create(
                        (pos, state) -> new WarpCoreControllerBlockEntity(
                                WARP_CORE_CONTROLLER_ENTITY, pos, state),
                        WARP_CORE_CONTROLLER).build()
        );

        CHARACTER_CREATION_TERMINAL_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(SecondDawnRP.MOD_ID, "character_creation_terminal"),
                BlockEntityType.Builder.create(
                        (pos, state) ->
                                new net.shard.seconddawnrp.character.CharacterCreationTerminalBlock
                                        .CharacterCreationTerminalBlockEntity(pos, state),
                        CHARACTER_CREATION_TERMINAL).build()
        );

        net.shard.seconddawnrp.character.CharacterCreationTerminalBlock
                .CharacterCreationTerminalBlockEntity.TYPE = CHARACTER_CREATION_TERMINAL_ENTITY;

        // Set the static TYPE reference — used by the no-arg convenience constructor
        WarpCoreControllerBlockEntity.TYPE = WARP_CORE_CONTROLLER_ENTITY;

        // Register EnergyStorage.SIDED so TR/EP cables can extract energy
        team.reborn.energy.api.EnergyStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> blockEntity.energyStorage,
                WARP_CORE_CONTROLLER_ENTITY
        );


    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static <T extends Block> T register(String id, T block) {
        T registered = Registry.register(Registries.BLOCK,
                Identifier.of(SecondDawnRP.MOD_ID, id), block);
        Registry.register(Registries.ITEM,
                Identifier.of(SecondDawnRP.MOD_ID, id),
                new BlockItem(registered, new Item.Settings()));
        return registered;
    }
}