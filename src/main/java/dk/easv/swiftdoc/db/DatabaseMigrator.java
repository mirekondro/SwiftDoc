package dk.easv.swiftdoc.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigrator {
    private static volatile boolean migrated;

    private DatabaseMigrator() {
    }

    public static void migrate(Connection connection) throws SQLException {
        if (migrated) {
            return;
        }
        synchronized (DatabaseMigrator.class) {
            if (migrated) {
                return;
            }
            ensureProfileColumns(connection);
            ensureDocumentColumns(connection);
            ensureUsersTable(connection);
            migrated = true;
        }
    }

    /**
     * Mirrors src/main/resources/db/users-setup.sql so the app boots
     * out-of-the-box. Re-running the script manually is still safe.
     */
    private static void ensureUsersTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Create table if it doesn't exist at all.
            stmt.execute(
                    "IF OBJECT_ID('dbo.Users', 'U') IS NULL "
                            + "BEGIN "
                            + "  CREATE TABLE dbo.Users ("
                            + "    UserId INT IDENTITY(1,1) PRIMARY KEY, "
                            + "    Username NVARCHAR(64) NOT NULL UNIQUE, "
                            + "    PasswordHash NVARCHAR(128) NOT NULL DEFAULT '', "
                            + "    Role NVARCHAR(16) NOT NULL DEFAULT 'USER'"
                            + "  ); "
                            + "END;");
            // Add missing columns if table existed with an old schema.
            stmt.execute(
                    "IF COL_LENGTH('dbo.Users', 'PasswordHash') IS NULL "
                            + "BEGIN ALTER TABLE dbo.Users ADD PasswordHash NVARCHAR(128) NOT NULL DEFAULT ''; END;");
            stmt.execute(
                    "IF COL_LENGTH('dbo.Users', 'Role') IS NULL "
                            + "BEGIN ALTER TABLE dbo.Users ADD Role NVARCHAR(16) NOT NULL DEFAULT 'USER'; END;");
            // Seed default accounts if none exist yet.
            stmt.execute(
                    "IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Username IN ('admin','user')) "
                            + "BEGIN "
                            + "  INSERT INTO dbo.Users (Username, PasswordHash, Role) VALUES "
                            + "    ('admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', 'ADMIN'), "
                            + "    ('user',  '04f8996da763b7a969b1028ee3007569eaf3a635486ddab211d512c85b9df8fb', 'USER'); "
                            + "END;");
        }
    }

    private static void ensureProfileColumns(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                    "IF COL_LENGTH('dbo.Profiles', 'SplitRule') IS NULL "
                            + "BEGIN ALTER TABLE dbo.Profiles ADD SplitRule NVARCHAR(255) NULL; END;");
            stmt.execute(
                    "IF COL_LENGTH('dbo.Profiles', 'DuplicateDetectionEnabled') IS NULL "
                            + "BEGIN ALTER TABLE dbo.Profiles ADD DuplicateDetectionEnabled BIT NOT NULL DEFAULT 0; END;");
        }
    }

    private static void ensureDocumentColumns(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Status column for document QA workflow (US-17).
            // Default 'NEW' so existing rows get a sensible starting status.
            stmt.execute(
                    "IF COL_LENGTH('dbo.Documents', 'Status') IS NULL "
                            + "BEGIN "
                            + "  ALTER TABLE dbo.Documents "
                            + "    ADD Status NVARCHAR(20) NOT NULL "
                            + "    CONSTRAINT DF_Documents_Status DEFAULT 'NEW' "
                            + "    WITH VALUES; "
                            + "END;");
        }
    }
}