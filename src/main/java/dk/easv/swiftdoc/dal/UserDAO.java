package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.model.User.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {

    private static final String SELECT_BY_USERNAME =
            "SELECT UserId, Username, PasswordHash, Role FROM dbo.Users WHERE Username = ?";
    private static final String SELECT_ALL =
            "SELECT UserId, Username, Role FROM dbo.Users ORDER BY Role DESC, Username";
    private static final String INSERT =
            "INSERT INTO dbo.Users (Username, PasswordHash, Role) VALUES (?, ?, ?)";
    private static final String DELETE =
            "DELETE FROM dbo.Users WHERE UserId = ?";

    public Optional<AuthRow> findByUsername(String username) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_USERNAME)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                User user = new User(
                        rs.getInt("UserId"),
                        rs.getString("Username"),
                        Role.valueOf(rs.getString("Role"))
                );
                return Optional.of(new AuthRow(user, rs.getString("PasswordHash")));
            }
        }
    }

    public List<User> getAll() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(new User(
                        rs.getInt("UserId"),
                        rs.getString("Username"),
                        Role.valueOf(rs.getString("Role"))
                ));
            }
        }
        return users;
    }

    public void create(String username, String passwordHash, Role role) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, role.name());
            stmt.executeUpdate();
        }
    }

    public void delete(int userId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    public record AuthRow(User user, String passwordHash) { }
}
