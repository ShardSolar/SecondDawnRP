package net.shard.shipyardsrp.starfleetarchives;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class PlayerProfileRepository {
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

    public boolean exists(UUID playerId) {
        return Files.exists(profilePaths.getProfilePath(playerId));
    }

    public Optional<PlayerProfile> load(UUID playerId) {
        Path path = profilePaths.getProfilePath(playerId);

        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(path);
            ProfileSaveData saveData = gson.fromJson(json, ProfileSaveData.class);
            return Optional.of(serializer.fromSaveData(saveData));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

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
}
