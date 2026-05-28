package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Document;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for {@link Document}.
 *
 * Schema: dbo.Documents (DocumentId, BoxId, BarcodeValue, DocumentStatus)
 */
public class DocumentDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.Documents (BoxId, BarcodeValue, DocumentStatus) VALUES (?, ?, ?)";

    private static final String COUNT_IN_BOX =
            "SELECT COUNT(*) FROM dbo.Documents WHERE BoxId = ?";

    private static final String SELECT_BY_BOX =
            "SELECT DocumentId, BoxId, BarcodeValue, DocumentStatus " +
                    "FROM dbo.Documents WHERE BoxId = ? ORDER BY DocumentId";

    private static final String UPDATE_STATUS =
            "UPDATE dbo.Documents SET DocumentStatus = ? WHERE DocumentId = ?";

    private static final String UPDATE_BARCODE =
            "UPDATE dbo.Documents SET BarcodeValue = ? WHERE DocumentId = ?";

    public Document create(int boxId, String barcodeValue) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {

            int documentNumber = countInBox(conn, boxId) + 1;
            Document.Status status = Document.Status.NEW;

            int newId;
            try (PreparedStatement insert = conn.prepareStatement(
                    INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

                insert.setInt(1, boxId);
                if (barcodeValue == null || barcodeValue.isBlank()) {
                    insert.setNull(2, Types.NVARCHAR);
                } else {
                    insert.setString(2, barcodeValue);
                }
                insert.setString(3, status.dbValue());

                int rows = insert.executeUpdate();
                if (rows != 1) {
                    throw new SQLException("Expected 1 row inserted, got " + rows);
                }

                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("INSERT returned no generated key for DocumentId");
                    }
                    newId = keys.getInt(1);
                }
            }

            return new Document(newId, boxId, documentNumber, barcodeValue, status);
        }
    }

    /**
     * @return all documents in the given box, ordered by DocumentId.
     *         documentNumber is computed from row position (1-based).
     */
    public List<Document> getByBox(int boxId) throws SQLException {
        List<Document> documents = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_BOX)) {

            stmt.setInt(1, boxId);
            try (ResultSet rs = stmt.executeQuery()) {
                int positionInBox = 0;
                while (rs.next()) {
                    positionInBox++;
                    documents.add(new Document(
                            rs.getInt("DocumentId"),
                            rs.getInt("BoxId"),
                            positionInBox,
                            rs.getString("BarcodeValue"),
                            Document.Status.fromDb(rs.getString("DocumentStatus"))
                    ));
                }
            }
        }
        return documents;
    }

    private static final String MAX_INCREMENTAL_IN_DOC =
            "SELECT ISNULL(MAX(IncrementalId), 0) FROM dbo.Files WHERE DocumentId = ?";

    private static final String SELECT_FILES_FOR_REASSIGN =
            "SELECT FileId FROM dbo.Files WHERE DocumentId = ? ORDER BY IncrementalId";

    private static final String REASSIGN_FILE =
            "UPDATE dbo.Files SET DocumentId = ?, IncrementalId = ? WHERE FileId = ?";

    private static final String DELETE_DOCUMENT =
            "DELETE FROM dbo.Documents WHERE DocumentId = ?";

    /**
     * Merge sourceDocId into targetDocId atomically:
     *   - All Files in source are reassigned to target, with IncrementalId
     *     continuing after target's current max.
     *   - target.BarcodeValue is updated to mergedBarcode.
     *   - source Document is deleted.
     */
    public void merge(int sourceDocId, int targetDocId, String mergedBarcode) throws SQLException {
        if (sourceDocId == targetDocId) {
            throw new IllegalArgumentException("Cannot merge a document into itself");
        }
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int nextIncremental;
                try (PreparedStatement maxStmt = conn.prepareStatement(MAX_INCREMENTAL_IN_DOC)) {
                    maxStmt.setInt(1, targetDocId);
                    try (ResultSet rs = maxStmt.executeQuery()) {
                        rs.next();
                        nextIncremental = rs.getInt(1) + 1;
                    }
                }

                List<Integer> sourceFileIds = new ArrayList<>();
                try (PreparedStatement selStmt = conn.prepareStatement(SELECT_FILES_FOR_REASSIGN)) {
                    selStmt.setInt(1, sourceDocId);
                    try (ResultSet rs = selStmt.executeQuery()) {
                        while (rs.next()) sourceFileIds.add(rs.getInt(1));
                    }
                }

                try (PreparedStatement upd = conn.prepareStatement(REASSIGN_FILE)) {
                    for (int fileId : sourceFileIds) {
                        upd.setInt(1, targetDocId);
                        upd.setInt(2, nextIncremental++);
                        upd.setInt(3, fileId);
                        upd.addBatch();
                    }
                    if (!sourceFileIds.isEmpty()) upd.executeBatch();
                }

                try (PreparedStatement bar = conn.prepareStatement(UPDATE_BARCODE)) {
                    if (mergedBarcode == null || mergedBarcode.isBlank()) {
                        bar.setNull(1, Types.NVARCHAR);
                    } else {
                        bar.setString(1, mergedBarcode);
                    }
                    bar.setInt(2, targetDocId);
                    bar.executeUpdate();
                }

                try (PreparedStatement del = conn.prepareStatement(DELETE_DOCUMENT)) {
                    del.setInt(1, sourceDocId);
                    del.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void updateBarcodeValue(int documentId, String barcodeValue) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_BARCODE)) {
            if (barcodeValue == null || barcodeValue.isBlank()) {
                stmt.setNull(1, Types.NVARCHAR);
            } else {
                stmt.setString(1, barcodeValue);
            }
            stmt.setInt(2, documentId);
            stmt.executeUpdate();
        }
    }

    public void updateStatus(int documentId, Document.Status status) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_STATUS)) {
            String value = status == null ? Document.Status.NEW.dbValue() : status.dbValue();
            stmt.setString(1, value);
            stmt.setInt(2, documentId);
            stmt.executeUpdate();
        }
    }

    private int countInBox(Connection conn, int boxId) throws SQLException {
        try (PreparedStatement count = conn.prepareStatement(COUNT_IN_BOX)) {
            count.setInt(1, boxId);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
