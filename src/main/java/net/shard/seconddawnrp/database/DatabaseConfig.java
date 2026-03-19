package net.shard.seconddawnrp.database;

import java.nio.file.Path;

public final class DatabaseConfig {

    private final Path databaseFile;

    public DatabaseConfig(Path configDir) {
        this.databaseFile = configDir.resolve("seconddawnrp").resolve("seconddawnrp.db");
    }

    public Path getDatabaseFile() {
        return databaseFile;
    }

    public String getJdbcUrl() {
        return "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }
}