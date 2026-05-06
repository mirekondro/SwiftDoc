package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Box;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Box}.
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

    private static final String SELECT_ALL =
            "SELECT BoxId, BoxName, ProfileId, GlobalRotation " +
                    "FROM dbo.Boxes ORDER BY BoxId";

    public Box create(String boxName, int profileId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement insert = conn.prepareStatement(
                     INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            insert.setString(1, boxName);
            insert.setInt(2, profileId);
            insert.setInt(3, 0);

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

            return fetchByIdInternal(conn, newId);
        }
    }

    public List<Box> getAll() throws SQLException {
        List<Box> boxes = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                boxes.add(mapRow(rs));
            }
        }
        return boxes;
    }

    /**
     * Fetch a box by its id.
     *
     * @return the box if found; empty if no row matches
     */
    public Optional<Box> getById(int boxId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {

            stmt.setInt(1, boxId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRow(rs));
            }
        }
    }

    private Box fetchByIdInternal(Connection conn, int boxId) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement(SELECT_BY_ID)) {
            select.setInt(1, boxId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Newly inserted BoxId " + boxId + " not found");
                }
                return mapRow(rs);
            }
        }
    }

    private Box mapRow(ResultSet rs) throws SQLException {
        return new Box(
                rs.getInt("BoxId"),
                rs.getString("BoxName"),
                rs.getInt("ProfileId"),
                rs.getInt("GlobalRotation")
        );
    }
}
