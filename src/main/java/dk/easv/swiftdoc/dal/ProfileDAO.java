package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.ScanningProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
            "SELECT p.ProfileId, p.ProfileName, p.SplitRule, c.ClientId, c.ClientName "
                    + "FROM dbo.Profiles p "
                    + "INNER JOIN dbo.Clients c ON p.ClientId = c.ClientId "
                    + "ORDER BY c.ClientName, p.ProfileName";

    private static final String INSERT_PROFILE =
            "INSERT INTO dbo.Profiles (ProfileName, ClientId, SplitRule) VALUES (?, ?, ?)";

    public List<ScanningProfile> getAll() throws SQLException {
        List<ScanningProfile> profiles = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                profiles.add(new ScanningProfile(
                        rs.getInt("ProfileId"),
                        rs.getString("ProfileName"),
                        rs.getString("SplitRule"),
                        rs.getInt("ClientId"),
                        rs.getString("ClientName")
                ));
            }
        }
        return profiles;
    }

    public ScanningProfile create(String profileName, int clientId, String splitRule) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_PROFILE, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, profileName);
            stmt.setInt(2, clientId);
            stmt.setString(3, splitRule);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int profileId = keys.getInt(1);
                    return new ScanningProfile(profileId, profileName, splitRule, clientId, null);
                }
            }
        }
        throw new SQLException("Failed to create profile (no key returned).");
    }
}
