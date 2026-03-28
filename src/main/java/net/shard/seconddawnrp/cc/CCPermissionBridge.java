package net.shard.seconddawnrp.cc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.UUID;

/**
 * Enforces permission checks for ComputerCraft write methods.
 *
 * Write methods (warpcore startup/shutdown/reset, future helm/weapons) must use
 * the same permission checks as PADD and terminal interactions. A Lua program
 * running on a CC computer cannot bypass the permission system.
 *
 * Note: WarpCoreService already enforces its own permission check inside
 * initiateStartup/initiateShutdown/resetFromFailed — so WarpCorePeripheral
 * doesn't need to call this bridge separately. It is retained here for future
 * peripherals whose service methods don't self-check.
 */
public class CCPermissionBridge {

    /**
     * Requires the given player to be online and hold the permission node.
     * Throws an Exception (surfaced to Lua as an error string) if denied.
     */
    public static void requirePermission(MinecraftServer server, UUID playerUuid, String node)
            throws Exception {
        if (playerUuid == null) {
            throw new Exception("Write methods require an authenticated player. No UUID provided.");
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player == null) {
            throw new Exception("Player is not online. Write methods require the player to be present.");
        }
        boolean allowed;
        try {
            allowed = SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, node);
        } catch (Exception e) {
            allowed = player.hasPermissionLevel(2);
        }
        if (!allowed) {
            throw new Exception("Permission denied. Required: " + node);
        }
    }

    /**
     * Non-throwing variant — returns true/false without surfacing an error.
     */
    public static boolean hasPermission(MinecraftServer server, UUID playerUuid, String node) {
        if (playerUuid == null) return false;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player == null) return false;
        try {
            return SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, node);
        } catch (Exception e) {
            return player.hasPermissionLevel(2);
        }
    }
}