package net.shard.seconddawnrp.character;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Physical block for the Character Creation Terminal.
 *
 * <p>Right-clicking opens the Character Creation GUI, sending an
 * {@link OpenCharacterCreationS2CPacket} to the client with the
 * current species list and the player's existing character data.
 *
 * <p>Registered in {@link net.shard.seconddawnrp.registry.ModBlocks}.
 */
public class CharacterCreationTerminalBlock extends BlockWithEntity {

    public static final MapCodec<CharacterCreationTerminalBlock> CODEC =
            MapCodec.unit(CharacterCreationTerminalBlock::new);

    // no-arg constructor required by codec
    public CharacterCreationTerminalBlock() {
        this(Settings.create());
    }

    public CharacterCreationTerminalBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CharacterCreationTerminalBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        // Get or create the player's active character profile
        CharacterProfile profile = SecondDawnRP.CHARACTER_SERVICE
                .get(sp.getUuid())
                .orElseGet(() -> SecondDawnRP.CHARACTER_SERVICE.getOrCreate(sp));

        // Build and send the opening packet
        OpenCharacterCreationS2CPacket packet =
                OpenCharacterCreationS2CPacket.build(SecondDawnRP.SPECIES_REGISTRY, profile);
        ServerPlayNetworking.send(sp, packet);

        return ActionResult.SUCCESS;
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    public static class CharacterCreationTerminalBlockEntity extends BlockEntity {

        public static BlockEntityType<CharacterCreationTerminalBlockEntity> TYPE;

        public CharacterCreationTerminalBlockEntity(BlockPos pos, BlockState state) {
            super(TYPE, pos, state);
        }
    }
}