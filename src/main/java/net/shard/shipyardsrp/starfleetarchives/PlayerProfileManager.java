package net.shard.shipyardsrp.starfleetarchives;

import net.shard.shipyardsrp.starfleetarchives.persistence.ProfileRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerProfileManager {

    private final ProfileRepository repository;
    private final DefaultProfileFactory defaultProfileFactory;
    private final Map<UUID, PlayerProfile> loadedProfiles = new HashMap<>();
    private final Set<UUID> dirtyProfiles = new HashSet<>();


    public PlayerProfileManager(ProfileRepository repository, DefaultProfileFactory defaultProfileFactory) {
        this.repository = repository;
        this.defaultProfileFactory = defaultProfileFactory;
    }

    public PlayerProfile getOrLoadProfile(UUID playerId, String playerName) {
        PlayerProfile loaded = loadedProfiles.get(playerId);
        if (loaded != null) {
            return loaded;
        }

        PlayerProfile profile = repository.load(playerId)
                .orElseGet(() -> defaultProfileFactory.create(playerId, playerName));

        loadedProfiles.put(playerId, profile);
        markDirty(playerId); // ensures first-time profiles get saved
        return profile;
    }

    public PlayerProfile getLoadedProfile(UUID playerId) {
        return loadedProfiles.get(playerId);
    }

    public boolean isLoaded(UUID playerId) {
        return loadedProfiles.containsKey(playerId);
    }

    public void markDirty(UUID playerId) {
        dirtyProfiles.add(playerId);
    }

    public void saveProfile(UUID playerId) {
        PlayerProfile profile = loadedProfiles.get(playerId);
        if (profile == null) {
            return;
        }

        repository.save(profile);
        dirtyProfiles.remove(playerId);
    }

    public void saveAll() {
        for (UUID playerId : new HashSet<>(loadedProfiles.keySet())) {
            saveProfile(playerId);
        }
    }

    public void unloadProfile(UUID playerId) {
        saveProfile(playerId);
        loadedProfiles.remove(playerId);
        dirtyProfiles.remove(playerId);
    }
}