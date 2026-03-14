package net.shard.shipyardsrp.database;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager implements AutoCloseable {

    private final DatabaseConfig config;
    private Connection connection;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
    }

    public void init() throws SQLException, IOException, ClassNotFoundException {
        Files.createDirectories(config.getDatabaseFile().getParent());
        Class.forName("org.sqlite.JDBC");
        this.connection = DriverManager.getConnection(config.getJdbcUrl());
        this.connection.setAutoCommit(true);
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is not initialized or already closed.");
        }
        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}