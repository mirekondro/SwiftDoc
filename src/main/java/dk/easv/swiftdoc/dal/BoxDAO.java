package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Box;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Data access for {@link Box}.
 *
 * Sprint 1 needs only INSERT (create a new box at session start).
 * SELECT/UPDATE/DELETE arrive later as other stories pull them in.
 */
public class BoxDAO {

    private static final String INSERT_SQL =
            "INSERT INTO boxes (box_name, profile_id, created_by) VALUES (?, ?, ?)";

    private static final String SELECT_BY_ID =
            "SELECT box_id, box_name, profile_id, created_by, created_at " +
                    "FROM boxes WHERE box_id = ?";

    /**
     * Insert a new box and return the fully-populated Box (with DB-generated
     * id and timestamp).
     *
     * @param boxName       human-entered box label (required, non-blank)
     * @param profileId     FK to scanning_profiles
     * @param createdBy     FK to users (Sprint 1: hardcoded devuser id)
     * @return the persisted Box with its assigned box_id
     * @throws SQLException on any DB error
     */
    public Box create(String boxName, int profileId, int createdBy) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement insert = conn.prepareStatement(
                     INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            insert.setString(1, boxName);
            insert.setInt(2, profileId);
            insert.setInt(3, createdBy);

            int rowsAffected = insert.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException("Expected 1 row inserted, got " + rowsAffected);
            }

            int newBoxId;
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("INSERT returned no generated key for box_id");
                }
                newBoxId = keys.getInt(1);
            }

            // Re-read the row to pick up the DB-assigned timestamp.
            return fetchById(conn, newBoxId);
        }
    }

    private Box fetchById(Connection conn, int boxId) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement(SELECT_BY_ID)) {
            select.setInt(1, boxId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Newly inserted box_id " + boxId + " not found");
                }
                Timestamp created = rs.getTimestamp("created_at");
                LocalDateTime createdAt = (created != null) ? created.toLocalDateTime() : null;

                return new Box(
                        rs.getInt("box_id"),
                        rs.getString("box_name"),
                        rs.getInt("profile_id"),
                        rs.getInt("created_by"),
                        createdAt
                );
            }
        }
    }
}
