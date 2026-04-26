package dk.easv.swiftdoc.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {
    // Singleton instance reference.
    private static volatile DBConnection instance;

    private String url;
    private String username;
    private String password;

    // Shared connection object used across the application.
    private Connection connection;

    // Private constructor that loads credentials from config.properties
    private DBConnection() {
        Properties props = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/config.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find config.properties in resources folder.");
            }
            props.load(input);
            this.url = props.getProperty("db.url");
            this.username = props.getProperty("db.username");
            this.password = props.getProperty("db.password");
        } catch (IOException ex) {
            throw new RuntimeException("Error loading database configuration", ex);
        }
    }

    // Thread-safe global access point
    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    // Returns the active connection; creates it lazily if missing/closed.
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            synchronized (this) {
                if (connection == null || connection.isClosed()) {
                    connection = DriverManager.getConnection(url, username, password);
                }
            }
        }
        return connection;
    }

    // Graceful shutdown helper.
    public synchronized void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}