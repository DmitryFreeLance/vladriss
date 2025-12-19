package ru.vadirss.bot.db;

import ru.vadirss.bot.config.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class Database {
    private final Config cfg;
    private final String jdbcUrl;

    public Database(Config cfg) throws IOException {
        this.cfg = Objects.requireNonNull(cfg);
        Files.createDirectories(cfg.dbPath().toAbsolutePath().getParent());
        this.jdbcUrl = "jdbc:sqlite:" + cfg.dbPath().toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        // WAL improves concurrency for sqlite in multi-thread environment.
        try {
            c.createStatement().execute("PRAGMA journal_mode=WAL;");
            c.createStatement().execute("PRAGMA foreign_keys=ON;");
            c.createStatement().execute("PRAGMA busy_timeout=5000;");
        } catch (SQLException ignored) { }
        return c;
    }

    public Config config() {
        return cfg;
    }
}
