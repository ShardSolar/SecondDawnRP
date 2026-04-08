package net.shard.seconddawnrp.tactical.damage;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.tactical.data.DamageZone;

import java.util.List;

/**
 * Pure executor — receives block lists from DamageZone objects and performs
 * world manipulation. Owns no state of its own.
 *
 * destroyZone()  — replaces real ship blocks with damage variants, breaks model blocks
 * restoreZone()  — restores real ship blocks to stone bricks
 */
public class DamageModelMapper {

    private static final Block[] DAMAGE_VARIANTS = {
            Blocks.AIR,
            Blocks.FIRE,
            Blocks.BLACK_CONCRETE,
            Blocks.GRAY_CONCRETE
    };

    // ── Destruction ───────────────────────────────────────────────────────────

    /**
     * Physically destroy a zone.
     * modelWorld — the world containing the schematic/model blocks (may be null if unused)
     * realWorld  — the world containing the actual ship build
     */
    public static void destroyZone(DamageZone zone,
                                   ServerWorld modelWorld,
                                   ServerWorld realWorld) {
        if (zone == null) return;

        // Break model blocks
        if (modelWorld != null) {
            for (BlockPos pos : zone.getModelBlocks()) {
                modelWorld.breakBlock(pos, false);
            }
        }

        // Replace real ship blocks with damage variants
        if (realWorld != null) {
            List<BlockPos> realBlocks = zone.getRealShipBlocks();
            for (int i = 0; i < realBlocks.size(); i++) {
                Block variant = DAMAGE_VARIANTS[i % DAMAGE_VARIANTS.length];
                realWorld.setBlockState(realBlocks.get(i), variant.getDefaultState());
            }
        }

        System.out.println("[Tactical] Zone " + zone.getZoneId()
                + " on " + zone.getShipId() + " physically destroyed.");
    }

    /**
     * Restore a zone's real ship blocks to stone bricks.
     * Full implementation would snapshot original block states at registration time.
     */
    public static void restoreZone(DamageZone zone, ServerWorld realWorld) {
        if (zone == null || realWorld == null) return;

        for (BlockPos pos : zone.getRealShipBlocks()) {
            realWorld.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState());
        }

        System.out.println("[Tactical] Zone " + zone.getZoneId()
                + " on " + zone.getShipId() + " physically restored.");
    }
}