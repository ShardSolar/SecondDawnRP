package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.divison.Rank;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ProfileSyncService {
    CompletableFuture<Void> syncProfile(ServerPlayerEntity player, PlayerProfile profile);

    CompletableFuture<Void> syncDivision(ServerPlayerEntity player, Division division);

    CompletableFuture<Void> syncRank(ServerPlayerEntity player, Rank rank);

    CompletableFuture<Void> syncBillet(ServerPlayerEntity player, Billet billet);

    CompletableFuture<Void> syncCertifications(ServerPlayerEntity player, Set<Certification> certifications);
}