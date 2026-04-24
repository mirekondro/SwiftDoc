package dk.easv.swiftdoc.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    // Singleton instance reference. volatile is required for safe double-checked locking.
    private static volatile DBConnection instance;

    // Placeholder credentials for your database setup.
    private static final String URL = "jdbc:mysql://localhost:3306/weblager";
    private static final String USERNAME = "weblager_user";
    private static final String PASSWORD = "change_me";

    // Shared connection object used across the application.
    private Connection connection;

    // Private constructor prevents external instantiation.
    private DBConnection() {
    }

    // Thread-safe global access point to the singleton instance.
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
                    connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                }
            }
        }
        return connection;
    }

    // Optional helper for graceful shutdown.
    public synchronized void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}

