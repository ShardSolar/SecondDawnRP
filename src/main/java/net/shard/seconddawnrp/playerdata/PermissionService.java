package net.shard.seconddawnrp.playerdata;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.divison.Rank;

public class PermissionService {
    private final LuckPerms luckPerms;

    public PermissionService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public boolean hasPermission(ServerPlayerEntity player, String node) {
        if (luckPerms == null) {
            return player.hasPermissionLevel(4);
        }

        User user = luckPerms.getUserManager().getUser(player.getUuid());
        if (user == null) {
            return false;
        }

        CachedPermissionData permissionData = user.getCachedData().getPermissionData();
        return permissionData.checkPermission(node).asBoolean();
    }

    public boolean canAssignTasks(PlayerProfile profile, ServerPlayerEntity player) {
        return hasPermission(player, "st.task.assign") || profile.getRank().getAuthorityLevel() >= 2;
    }

    public boolean canApproveTasks(PlayerProfile profile, ServerPlayerEntity player) {
        return hasPermission(player, "st.task.approve") || profile.getRank().getAuthorityLevel() >= 3;
    }

    public boolean canPromote(PlayerProfile profile, Rank targetRank, ServerPlayerEntity player) {
        return hasPermission(player, "st.profile.promote")
                || profile.getRank().getAuthorityLevel() > targetRank.getAuthorityLevel();
    }
}