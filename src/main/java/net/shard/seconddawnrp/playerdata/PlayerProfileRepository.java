package net.shard.seconddawnrp.playerdata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.shard.seconddawnrp.playerdata.persistence.ProfileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerProfileRepository implements ProfileRepository {

    private final ProfilePaths profilePaths;
    private final ProfileSerializer serializer;
    private final Gson gson;

    public PlayerProfileRepository(ProfilePaths profilePaths, ProfileSerializer serializer) {
        this.profilePaths = profilePaths;
        this.serializer = serializer;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws IOException {
        Files.createDirectories(profilePaths.getProfilesDirectory());
    }

    @Override
    public Optional<PlayerProfile> load(UUID playerId) {
        Path path = profilePaths.getProfilePath(playerId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(path);
            ProfileSaveData saveData = gson.fromJson(json, ProfileSaveData.class);
            return Optional.ofNullable(serializer.fromSaveData(saveData));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public void save(PlayerProfile profile) {
        Path path = profilePaths.getProfilePath(profile.getPlayerId());
        try {
            Files.createDirectories(profilePaths.getProfilesDirectory());
            ProfileSaveData saveData = serializer.toSaveData(profile);
            String json = gson.toJson(saveData);
            Files.writeString(path, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(UUID playerUuid) {
        try {
            Files.deleteIfExists(profilePaths.getProfilePath(playerUuid));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Collection<PlayerProfile> loadAll() {
        return List.of();
    }
}