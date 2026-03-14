package net.shard.shipyardsrp.starfleetarchives;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class NoOpProfileSyncService implements ProfileSyncService {

    @Override
    public CompletableFuture<Void> syncProfile(ServerPlayerEntity player, PlayerProfile profile) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> syncDivision(ServerPlayerEntity player, Division division) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> syncRank(ServerPlayerEntity player, Rank rank) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> syncBillet(ServerPlayerEntity player, Billet billet) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> syncCertifications(ServerPlayerEntity player, Set<Certification> certifications) {
        return CompletableFuture.completedFuture(null);
    }
}