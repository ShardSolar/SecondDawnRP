package net.shard.seconddawnrp.tasksystem.util;

import net.minecraft.server.network.ServerPlayerEntity;

public final class TaskPermissionUtil {

    private TaskPermissionUtil() {
    }

    public static boolean canOpenOperationsPad(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }
}