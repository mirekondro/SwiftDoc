package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Document;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Data access for {@link Document}.
 *
 * Sprint 1: INSERT only.
 *
 * Schema: dbo.Documents (DocumentId, BoxId, BarcodeValue)
 *
 * Note: documentNumber is computed at runtime (COUNT(*) in the box) and
 * is not persisted. If the team later adds a DocumentNumber column for
 * audit/display, swap the COUNT for that.
 */
public class DocumentDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.Documents (BoxId, BarcodeValue) VALUES (?, ?)";

    private static final String COUNT_IN_BOX =
            "SELECT COUNT(*) FROM dbo.Documents WHERE BoxId = ?";

    /**
     * Create a new document in the given box.
     *
     * @param boxId         parent box
     * @param barcodeValue  barcode text from the separator that triggered this
     *                      document, or null for the box's first document
     * @return the persisted Document with DB-assigned DocumentId and computed
     *         documentNumber (1-based within the box)
     */
    public Document create(int boxId, String barcodeValue) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {

            int documentNumber = countInBox(conn, boxId) + 1;

            int newId;
            try (PreparedStatement insert = conn.prepareStatement(
                    INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

                insert.setInt(1, boxId);
                if (barcodeValue == null || barcodeValue.isBlank()) {
                    insert.setNull(2, Types.NVARCHAR);
                } else {
                    insert.setString(2, barcodeValue);
                }

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

            return new Document(newId, boxId, documentNumber, barcodeValue);
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
