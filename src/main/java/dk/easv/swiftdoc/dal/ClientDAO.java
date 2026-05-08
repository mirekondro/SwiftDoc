package dk.easv.swiftdoc.dal;

import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.Client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for Clients.
 */
public class ClientDAO {

    private static final String SELECT_ALL =
            "SELECT ClientId, ClientName FROM dbo.Clients ORDER BY ClientName";

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
}

