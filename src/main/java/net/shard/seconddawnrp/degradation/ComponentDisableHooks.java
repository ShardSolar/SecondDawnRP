package net.shard.seconddawnrp.degradation;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.service.DegradationService;

public final class ComponentDisableHooks {

    private ComponentDisableHooks() {}

    public static boolean isLocked(ServerWorld world, BlockPos pos) {
        return isLocked(world, pos.asLong());
    }

    public static boolean isLocked(ServerWorld world, long blockPosLong) {
        DegradationService service = SecondDawnRP.DEGRADATION_SERVICE;
        if (service == null) return false;

        return service.isBlockDisabled(
                world.getRegistryKey().getValue().toString(),
                blockPosLong
        );
    }
}