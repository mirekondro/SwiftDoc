package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.ScanningProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for {@link ScanningProfile}.
 *
 * Sprint 1 only needs read access (populate the New Scan dropdown).
 * Full CRUD comes in Sprint 3 with US-07.
 */
public class ProfileDAO {

    private static final String SELECT_ALL =
            "SELECT profile_id, profile_name, description, barcode_split_rule, created_by " +
                    "FROM scanning_profiles " +
                    "ORDER BY profile_name";

    /**
     * @return all scanning profiles ordered by name. Empty list if none.
     */
    public List<ScanningProfile> getAll() throws SQLException {
        List<ScanningProfile> profiles = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                profiles.add(new ScanningProfile(
                        rs.getInt("profile_id"),
                        rs.getString("profile_name"),
                        rs.getString("description"),
                        rs.getString("barcode_split_rule"),
                        rs.getInt("created_by")
                ));
            }
        }
        return profiles;
    }
}
