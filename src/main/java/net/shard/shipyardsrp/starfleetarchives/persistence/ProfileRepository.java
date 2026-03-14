package net.shard.shipyardsrp.starfleetarchives.persistence;

import net.shard.shipyardsrp.starfleetarchives.PlayerProfile;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Optional<PlayerProfile> load(UUID playerUuid);
    void save(PlayerProfile profile);
    void delete(UUID playerUuid);
    Collection<PlayerProfile> loadAll();
}