package net.shard.shipyardsrp.starfleetarchives;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsSyncService implements ProfileSyncService {
    private final LuckPerms luckPerms;
    private final LuckPermsGroupMapper groupMapper;

    public LuckPermsSyncService(LuckPerms luckPerms, LuckPermsGroupMapper groupMapper) {
        this.luckPerms = luckPerms;
        this.groupMapper = groupMapper;
    }

    @Override
    public CompletableFuture<Void> syncProfile(ServerPlayerEntity player, PlayerProfile profile) {
        return loadUser(player.getUuid()).thenAccept(user -> {
            syncDivisionInternal(user, profile.getDivision());
            syncRankInternal(user, profile.getRank());
            syncBilletsInternal(user, profile.getBillets());
            syncCertificationsInternal(user, profile.getCertifications());
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncDivision(ServerPlayerEntity player, Division division) {
        return loadUser(player.getUuid()).thenAccept(user -> {
            syncDivisionInternal(user, division);
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncRank(ServerPlayerEntity player, Rank rank) {
        return loadUser(player.getUuid()).thenAccept(user -> {
            syncRankInternal(user, rank);
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncBillet(ServerPlayerEntity player, Billet billet) {
        return loadUser(player.getUuid()).thenAccept(user -> {
            User liveUser = user;
            liveUser.data().add(InheritanceNode.builder(groupMapper.getBilletGroup(billet)).build());
            luckPerms.getUserManager().saveUser(liveUser);
        });
    }

    public CompletableFuture<Void> syncBillets(ServerPlayerEntity player, Set<Billet> billets) {
        return loadUser(player.getUuid()).thenAccept(user -> {
            syncBilletsInternal(user, billets);
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> syncCertifications(ServerPlayerEntity player, Set<Certification> certifications) {
        return loadUser(player.getUuid()).thenAccept(user -> {
            syncCertificationsInternal(user, certifications);
            luckPerms.getUserManager().saveUser(user);
        });
    }

    private CompletableFuture<User> loadUser(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user != null) {
            return CompletableFuture.completedFuture(user);
        }
        return luckPerms.getUserManager().loadUser(uuid);
    }

    private void syncDivisionInternal(User user, Division division) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && inheritanceNode.getGroupName().startsWith("st.division."));
        user.data().add(InheritanceNode.builder(groupMapper.getDivisionGroup(division)).build());
    }

    private void syncRankInternal(User user, Rank rank) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && inheritanceNode.getGroupName().startsWith("st.rank."));
        user.data().add(InheritanceNode.builder(groupMapper.getRankGroup(rank)).build());
    }

    private void syncBilletsInternal(User user, Set<Billet> billets) {
        user.data().clear(node -> node instanceof InheritanceNode inheritanceNode
                && inheritanceNode.getGroupName().startsWith("st.billet."));

        for (Billet billet : billets) {
            if (billet != Billet.NONE) {
                user.data().add(InheritanceNode.builder(groupMapper.getBilletGroup(billet)).build());
            }
        }
    }

    private void syncCertificationsInternal(User user, Set<Certification> certifications) {
        user.data().clear(node -> node.getKey().startsWith("st.cert."));

        for (Certification certification : certifications) {
            Node node = Node.builder(groupMapper.getCertificationNode(certification)).value(true).build();
            user.data().add(node);
        }
    }
}