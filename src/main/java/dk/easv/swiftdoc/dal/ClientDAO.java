package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.sql.Statement;

/**
 * Data access for Clients.
 */
public class ClientDAO {

    private static final String SELECT_ALL =
            "SELECT ClientId, ClientName FROM dbo.Clients ORDER BY ClientName";

    private static final String INSERT_CLIENT =
            "INSERT INTO dbo.Clients (ClientName) VALUES (?)";

    private static final String SELECT_BY_NAME =
            "SELECT ClientId FROM dbo.Clients WHERE ClientName = ?";

    public List<Client> getAll() throws SQLException {
        List<Client> clients = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                clients.add(new Client(
                        rs.getInt("ClientId"),
                        rs.getString("ClientName")
                ));
            }
        }
        return clients;
    }
    public boolean existsByName(String clientName) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_NAME)) {
            stmt.setString(1, clientName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Client create(String clientName) throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_CLIENT, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, clientName);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Client(keys.getInt(1), clientName);
                }
            }
        }
        throw new SQLException("Failed to create client (no key returned).");
    }
}

