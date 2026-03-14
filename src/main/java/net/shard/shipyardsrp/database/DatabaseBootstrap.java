package net.shard.shipyardsrp.database;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseBootstrap {

    private final DatabaseManager databaseManager;
    private final DatabaseMigrations migrations;

    public DatabaseBootstrap(DatabaseManager databaseManager, DatabaseMigrations migrations) {
        this.databaseManager = databaseManager;
        this.migrations = migrations;
    }

    public void bootstrap() throws SQLException {
        Connection connection = databaseManager.getConnection();
        migrations.migrate(connection);
    }
}