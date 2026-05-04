package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.File;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for {@link File}.
 *
 * Schema: dbo.Files (FileId, DocumentId, ReferenceId, IncrementalId, RotationAngle, TiffData)
 *
 * Note on TIFF bytes: getByDocument() does NOT load TiffData (lazy loading,
 * keeps the sidebar fast even with many files). Use getTiffData(fileId) to
 * fetch the bytes only when actually needed (e.g. when the user clicks a
 * file to view it).
 */
public class FileDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.Files " +
                    "(DocumentId, ReferenceId, IncrementalId, RotationAngle, TiffData) " +
                    "VALUES (?, ?, ?, ?, ?)";

    private static final String COUNT_FILES_IN_BOX =
            "SELECT COUNT(*) FROM dbo.Files f " +
                    "JOIN dbo.Documents d ON d.DocumentId = f.DocumentId " +
                    "WHERE d.BoxId = ?";

    private static final String COUNT_FILES_IN_DOCUMENT =
            "SELECT COUNT(*) FROM dbo.Files WHERE DocumentId = ?";

    /** Metadata-only query — no TiffData column, fast even with many big files. */
    private static final String SELECT_BY_DOCUMENT_METADATA =
            "SELECT FileId, DocumentId, ReferenceId, IncrementalId, RotationAngle " +
                    "FROM dbo.Files WHERE DocumentId = ? ORDER BY IncrementalId";

    private static final String SELECT_TIFF_DATA =
            "SELECT TiffData FROM dbo.Files WHERE FileId = ?";

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

    /**
     * @return files in the document ordered by IncrementalId.
     *         The returned File objects have null tiffData (use getTiffData
     *         to fetch the bytes when actually needed).
     */
    public List<File> getByDocument(int documentId) throws SQLException {
        List<File> files = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_DOCUMENT_METADATA)) {

            stmt.setInt(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(new File(
                            rs.getInt("FileId"),
                            rs.getInt("DocumentId"),
                            rs.getInt("ReferenceId"),
                            rs.getInt("IncrementalId"),
                            rs.getInt("RotationAngle"),
                            null   // tiffData fetched lazily
                    ));
                }
            }
        }
        return files;
    }

    /**
     * Fetch the TIFF bytes for a single file.
     *
     * @param fileId the file's primary key
     * @return raw TIFF bytes, or null if the row has no TiffData
     * @throws SQLException if the file id doesn't exist
     */
    public byte[] getTiffData(int fileId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_TIFF_DATA)) {

            stmt.setInt(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("FileId " + fileId + " not found");
                }
                return rs.getBytes("TiffData");
            }
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
