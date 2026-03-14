package net.shard.shipyardsrp.database;

import java.nio.file.Path;

public final class DatabaseConfig {

    private final Path databaseFile;

    public DatabaseConfig(Path configDir) {
        this.databaseFile = configDir.resolve("shipyardsrp").resolve("shipyardsrp.db");
    }

    public Path getDatabaseFile() {
        return databaseFile;
    }

    public String getJdbcUrl() {
        return "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }
}