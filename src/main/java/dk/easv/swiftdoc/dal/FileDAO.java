package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.File;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Data access for {@link File}.
 *
 * Sprint 1: INSERT + reference-id calculation.
 *
 * Schema: dbo.Files (FileId, DocumentId, ReferenceId, IncrementalId, RotationAngle, TiffData)
 *
 * ReferenceId is the scan order WITHIN A BOX (immutable audit of the order
 * pages came off the scanner). IncrementalId is the display order WITHIN A
 * DOCUMENT (mutable, US-15 lets users drag to reorder).
 *
 * Sprint 1 sets both to the same value as files arrive in scan order.
 */
public class FileDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.Files " +
                    "(DocumentId, ReferenceId, IncrementalId, RotationAngle, TiffData) " +
                    "VALUES (?, ?, ?, ?, ?)";

    /** Counts existing files in the box across all its documents. */
    private static final String COUNT_FILES_IN_BOX =
            "SELECT COUNT(*) FROM dbo.Files f " +
                    "JOIN dbo.Documents d ON d.DocumentId = f.DocumentId " +
                    "WHERE d.BoxId = ?";

    /** Counts existing files in just this document (for IncrementalId). */
    private static final String COUNT_FILES_IN_DOCUMENT =
            "SELECT COUNT(*) FROM dbo.Files WHERE DocumentId = ?";

    /**
     * Save a TIFF as a file row attached to the given document.
     *
     * @param documentId  parent document
     * @param boxId       parent box (used to compute ReferenceId across all docs)
     * @param tiffBytes   raw TIFF data
     * @return the persisted File with DB-assigned FileId
     */
    public File create(int documentId, int boxId, byte[] tiffBytes) throws SQLException {
        if (tiffBytes == null || tiffBytes.length == 0) {
            throw new IllegalArgumentException("tiffBytes must not be null or empty");
        }

        try (Connection conn = DBConnection.getInstance().getConnection()) {

            int referenceId = countFilesInBox(conn, boxId) + 1;
            int incrementalId = countFilesInDocument(conn, documentId) + 1;
            int rotationAngle = 0;

            int newId;
            try (PreparedStatement insert = conn.prepareStatement(
                    INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

                insert.setInt(1, documentId);
                insert.setInt(2, referenceId);
                insert.setInt(3, incrementalId);
                insert.setInt(4, rotationAngle);
                insert.setBytes(5, tiffBytes);

                int rows = insert.executeUpdate();
                if (rows != 1) {
                    throw new SQLException("Expected 1 row inserted, got " + rows);
                }

                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("INSERT returned no generated key for FileId");
                    }
                    newId = keys.getInt(1);
                }
            }

            return new File(newId, documentId, referenceId, incrementalId,
                    rotationAngle, tiffBytes);
        }
    }

    private int countFilesInBox(Connection conn, int boxId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(COUNT_FILES_IN_BOX)) {
            stmt.setInt(1, boxId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countFilesInDocument(Connection conn, int documentId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(COUNT_FILES_IN_DOCUMENT)) {
            stmt.setInt(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
