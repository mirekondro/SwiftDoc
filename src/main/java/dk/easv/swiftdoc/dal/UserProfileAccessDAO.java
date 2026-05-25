package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class UserProfileAccessDAO {

    private static final String SELECT_IDS =
            "SELECT ProfileId FROM dbo.UserProfileAccess WHERE UserId = ?";
    private static final String DELETE_FOR_USER =
            "DELETE FROM dbo.UserProfileAccess WHERE UserId = ?";
    private static final String INSERT =
            "INSERT INTO dbo.UserProfileAccess (UserId, ProfileId) VALUES (?, ?)";

    public Set<Integer> getProfileIdsForUser(int userId) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_IDS)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ProfileId"));
                }
            }
        }
        return ids;
    }

    public void setProfilesForUser(int userId, Collection<Integer> profileIds) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement(DELETE_FOR_USER)) {
                    del.setInt(1, userId);
                    del.executeUpdate();
                }
                if (!profileIds.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(INSERT)) {
                        for (int profileId : profileIds) {
                            ins.setInt(1, userId);
                            ins.setInt(2, profileId);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
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
}
