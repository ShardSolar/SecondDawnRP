package net.shard.shipyardsrp.starfleetarchives;

import java.nio.file.Path;
import java.util.UUID;

public class ProfilePaths {
    private final Path rootDirectory;
    private final Path profilesDirectory;

    public ProfilePaths(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.profilesDirectory = rootDirectory.resolve("assets/shipyardsrp").resolve("profiles");
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public Path getProfilesDirectory() {
        return profilesDirectory;
    }

    public Path getProfilePath(UUID playerId) {
        return profilesDirectory.resolve(playerId + ".json");
    }
}
