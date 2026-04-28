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
 * Sprint 1: only read access (populate the New Scan dropdown).
 * Full CRUD comes in Sprint 3 with US-07.
 */
public class ProfileDAO {

    private static final String SELECT_ALL =
            "SELECT ProfileId, ProfileName, SplitRule " +
                    "FROM dbo.Profiles " +
                    "ORDER BY ProfileName";

    public List<ScanningProfile> getAll() throws SQLException {
        List<ScanningProfile> profiles = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                profiles.add(new ScanningProfile(
                        rs.getInt("ProfileId"),
                        rs.getString("ProfileName"),
                        rs.getString("SplitRule")
                ));
            }
        }
        return profiles;
    }
}
