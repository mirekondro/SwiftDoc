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
            migrated = true;
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