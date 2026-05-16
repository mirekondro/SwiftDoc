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
import java.util.Optional;

/**
 * Data access for {@link ScanningProfile}.
 *
 * Profiles join with Clients so dropdowns and exports can display both
 * the client name and the profile name together.
 */
public class ProfileDAO {

    private static final String SELECT_ALL =
            "SELECT p.ProfileId, p.ProfileName, p.SplitRule, p.DuplicateDetectionEnabled, "
                    + "c.ClientId, c.ClientName "
                    + "FROM dbo.Profiles p "
                    + "INNER JOIN dbo.Clients c ON p.ClientId = c.ClientId "
                    + "ORDER BY c.ClientName, p.ProfileName";

    private static final String SELECT_BY_ID =
            "SELECT p.ProfileId, p.ProfileName, p.SplitRule, p.DuplicateDetectionEnabled, "
                    + "c.ClientId, c.ClientName "
                    + "FROM dbo.Profiles p "
                    + "INNER JOIN dbo.Clients c ON p.ClientId = c.ClientId "
                    + "WHERE p.ProfileId = ?";

    private static final String SELECT_FOR_USER =
            "SELECT p.ProfileId, p.ProfileName, p.SplitRule, p.DuplicateDetectionEnabled, "
                    + "c.ClientId, c.ClientName "
                    + "FROM dbo.Profiles p "
                    + "INNER JOIN dbo.Clients c ON p.ClientId = c.ClientId "
                    + "INNER JOIN dbo.UserProfileAccess upa ON upa.ProfileId = p.ProfileId "
                    + "WHERE upa.UserId = ? "
                    + "ORDER BY c.ClientName, p.ProfileName";

    private static final String INSERT_PROFILE =
            "INSERT INTO dbo.Profiles (ProfileName, ClientId, SplitRule, DuplicateDetectionEnabled) "
                    + "VALUES (?, ?, ?, ?)";

    public List<ScanningProfile> getAll() throws SQLException {
        List<ScanningProfile> profiles = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                profiles.add(mapRow(rs));
            }
        }
        return profiles;
    }

    /**
     * Fetch a profile by its id (with client info via JOIN).
     *
     * @return the profile if found; empty if no row matches
     */
    public Optional<ScanningProfile> getById(int profileId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {

            stmt.setInt(1, profileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRow(rs));
            }
        }
    }

    public List<ScanningProfile> getForUser(int userId) throws SQLException {
        List<ScanningProfile> profiles = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_FOR_USER)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    profiles.add(mapRow(rs));
                }
            }
        }
        return profiles;
    }

    public ScanningProfile create(String profileName, int clientId, String splitRule,
                                  boolean duplicateDetectionEnabled) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_PROFILE, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, profileName);
            stmt.setInt(2, clientId);
            stmt.setString(3, splitRule);
            stmt.setBoolean(4, duplicateDetectionEnabled);

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int profileId = keys.getInt(1);
                    // ClientName left null here — the create flow doesn't need
                    // it, and the dialog refresh will reload via getAll() which
                    // does the JOIN and fills it in.
                    return new ScanningProfile(profileId, profileName, splitRule, clientId, null,
                            duplicateDetectionEnabled);
                }
            }
        }
        throw new SQLException("Failed to create profile (no key returned).");
    }

    private ScanningProfile mapRow(ResultSet rs) throws SQLException {
        return new ScanningProfile(
                rs.getInt("ProfileId"),
                rs.getString("ProfileName"),
                rs.getString("SplitRule"),
                rs.getInt("ClientId"),
                rs.getString("ClientName"),
                rs.getBoolean("DuplicateDetectionEnabled")
        );
    }
}
