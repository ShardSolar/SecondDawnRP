package net.shard.shipyardsrp.starfleetarchives;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class PlayerProfileService {
    private final PlayerProfileManager profileManager;
    private final ProfileSyncService profileSyncService;

    public PlayerProfileService(PlayerProfileManager profileManager, ProfileSyncService profileSyncService) {
        this.profileManager = profileManager;
        this.profileSyncService = profileSyncService;
    }

    public PlayerProfile getOrLoad(ServerPlayerEntity player) {
        return profileManager.getOrLoadProfile(player.getUuid(), player.getGameProfile().getName());
    }

    public PlayerProfile getLoaded(UUID playerId) {
        return profileManager.getLoadedProfile(playerId);
    }

    public void setDivision(ServerPlayerEntity player, Division division) {
        PlayerProfile profile = getOrLoad(player);
        profile.setDivision(division);
        profileManager.markDirty(player.getUuid());
        profileSyncService.syncDivision(player, division);
    }

    public void setProgressionPath(ServerPlayerEntity player, ProgressionPath progressionPath) {
        PlayerProfile profile = getOrLoad(player);
        profile.setProgressionPath(progressionPath);
        profileManager.markDirty(player.getUuid());
    }

    public void setRank(ServerPlayerEntity player, Rank rank) {
        PlayerProfile profile = getOrLoad(player);
        profile.setRank(rank);
        profileManager.markDirty(player.getUuid());
        profileSyncService.syncRank(player, rank);
    }

    public void addBillet(ServerPlayerEntity player, Billet billet) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.addBillet(billet)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncProfile(player, profile);
        }
    }

    public void removeBillet(ServerPlayerEntity player, Billet billet) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.removeBillet(billet)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncProfile(player, profile);
        }
    }

    public void addRankPoints(ServerPlayerEntity player, int amount) {
        PlayerProfile profile = getOrLoad(player);
        profile.addRankPoints(amount);
        profileManager.markDirty(player.getUuid());
    }

    public void setDutyStatus(ServerPlayerEntity player, DutyStatus dutyStatus) {
        PlayerProfile profile = getOrLoad(player);
        profile.setDutyStatus(dutyStatus);
        profileManager.markDirty(player.getUuid());
    }

    public void setSupervisor(ServerPlayerEntity player, UUID supervisorId) {
        PlayerProfile profile = getOrLoad(player);
        profile.setSupervisorId(supervisorId);
        profileManager.markDirty(player.getUuid());
    }

    public void addCertification(ServerPlayerEntity player, Certification certification) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.addCertification(certification)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncCertifications(player, profile.getCertifications());
        }
    }

    public void removeCertification(ServerPlayerEntity player, Certification certification) {
        PlayerProfile profile = getOrLoad(player);
        if (profile.removeCertification(certification)) {
            profileManager.markDirty(player.getUuid());
            profileSyncService.syncCertifications(player, profile.getCertifications());
        }
    }

    public void syncAll(ServerPlayerEntity player) {
        PlayerProfile profile = getOrLoad(player);
        profileSyncService.syncProfile(player, profile);
    }
}