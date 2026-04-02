package net.shard.seconddawnrp.mixin.degradation;

import net.minecraft.block.AbstractBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.degradation.ComponentDisableHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateComparatorMixin {

    @Inject(method = "getComparatorOutput", at = @At("HEAD"), cancellable = true)
    private void seconddawnrp$disableComparatorOutput(
            World world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        if (world instanceof ServerWorld serverWorld
                && ComponentDisableHooks.isLocked(serverWorld, pos)) {
            cir.setReturnValue(0);
        }
    }
}