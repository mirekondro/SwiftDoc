package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Box;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Data access for {@link Box}.
 *
 * Sprint 1: INSERT only (create box at session start).
 * SELECT/UPDATE/DELETE arrive with later stories.
 *
 * Schema: dbo.Boxes (BoxId, BoxName, ProfileId, GlobalRotation)
 */
public class BoxDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.Boxes (BoxName, ProfileId, GlobalRotation) " +
                    "VALUES (?, ?, ?)";

    private static final String SELECT_BY_ID =
            "SELECT BoxId, BoxName, ProfileId, GlobalRotation " +
                    "FROM dbo.Boxes WHERE BoxId = ?";

    /**
     * Create a new box.
     *
     * @param boxName    user-entered label
     * @param profileId  FK to dbo.Profiles
     * @return the persisted Box with DB-assigned BoxId
     */
    public Box create(String boxName, int profileId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement insert = conn.prepareStatement(
                     INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            insert.setString(1, boxName);
            insert.setInt(2, profileId);
            insert.setInt(3, 0);   // GlobalRotation defaults to 0 in Sprint 1

            int rows = insert.executeUpdate();
            if (rows != 1) {
                throw new SQLException("Expected 1 row inserted, got " + rows);
            }

            int newId;
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("INSERT returned no generated key for BoxId");
                }
                newId = keys.getInt(1);
            }

            return fetchById(conn, newId);
        }
    }

    private Box fetchById(Connection conn, int boxId) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement(SELECT_BY_ID)) {
            select.setInt(1, boxId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Newly inserted BoxId " + boxId + " not found");
                }
                return new Box(
                        rs.getInt("BoxId"),
                        rs.getString("BoxName"),
                        rs.getInt("ProfileId"),
                        rs.getInt("GlobalRotation")
                );
            }
        }
    }
}
