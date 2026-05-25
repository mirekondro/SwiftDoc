package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.LogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LogDAO {

    private static final String INSERT_SQL =
            "INSERT INTO dbo.FileLogs (UserId, Username, [Action], FileId, DocumentId) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_ALL_SQL =
            "SELECT LogId, UserId, Username, [Action], FileId, DocumentId, CreatedAt " +
            "FROM dbo.FileLogs ORDER BY CreatedAt DESC";

    public void log(int userId, String username, LogEntry.Action action,
                    int fileId, int documentId) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            stmt.setInt(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, action.name());
            stmt.setInt(4, fileId);
            stmt.setInt(5, documentId);
            stmt.executeUpdate();
        }
    }

    public List<LogEntry> getAll() throws SQLException {
        List<LogEntry> entries = new ArrayList<>();
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                entries.add(new LogEntry(
                        rs.getInt("LogId"),
                        rs.getInt("UserId"),
                        rs.getString("Username"),
                        LogEntry.Action.valueOf(rs.getString("Action")),
                        rs.getInt("FileId"),
                        rs.getInt("DocumentId"),
                        rs.getTimestamp("CreatedAt").toLocalDateTime()
                ));
            }
        }
        return entries;
    }
}
