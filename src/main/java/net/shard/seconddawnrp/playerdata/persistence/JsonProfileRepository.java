package net.shard.seconddawnrp.playerdata.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.shard.seconddawnrp.playerdata.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class JsonProfileRepository implements ProfileRepository {

    private final ProfilePaths profilePaths;
    private final ProfileSerializer serializer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonProfileRepository(ProfilePaths profilePaths, ProfileSerializer serializer) {
        this.profilePaths = profilePaths;
        this.serializer = serializer;
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
            ProfileSaveData data = gson.fromJson(json, ProfileSaveData.class);
            return Optional.of(serializer.fromSaveData(data));
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

            ProfileSaveData data = serializer.toSaveData(profile);
            String json = gson.toJson(data);

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
        return List.of(); // fine for now
    }
}