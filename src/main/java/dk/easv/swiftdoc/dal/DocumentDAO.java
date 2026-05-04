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
 * Schema: dbo.Documents (DocumentId, BoxId, BarcodeValue)
 */
public class DocumentDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.Documents (BoxId, BarcodeValue) VALUES (?, ?)";

    private static final String COUNT_IN_BOX =
            "SELECT COUNT(*) FROM dbo.Documents WHERE BoxId = ?";

    private static final String SELECT_BY_BOX =
            "SELECT DocumentId, BoxId, BarcodeValue " +
                    "FROM dbo.Documents WHERE BoxId = ? ORDER BY DocumentId";

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
                            rs.getString("BarcodeValue")
                    ));
                }
            }
        }
        return documents;
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
