package net.shard.shipyardsrp.tasksystem.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class TaskTargetMatcher {

    private TaskTargetMatcher() {
    }

    public static boolean blockMatches(Block block, String targetId) {
        if (block == null || targetId == null || targetId.isBlank()) {
            return false;
        }

        Identifier blockId = Registries.BLOCK.getId(block);
        return blockId != null && targetId.equals(blockId.toString());
    }
}