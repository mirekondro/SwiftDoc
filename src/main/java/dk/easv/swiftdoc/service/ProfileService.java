package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.ClientDAO;
import dk.easv.swiftdoc.dal.ProfileDAO;
import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;

import java.sql.SQLException;
import java.util.List;

/**
 * Profile and client management.
 */
public class ProfileService {

    private final ProfileDAO profileDAO;
    private final ClientDAO clientDAO;

    public ProfileService() {
        this(new ProfileDAO(), new ClientDAO());
    }

    public ProfileService(ProfileDAO profileDAO, ClientDAO clientDAO) {
        this.profileDAO = profileDAO;
        this.clientDAO = clientDAO;
    }

    public List<Client> getClients() throws SQLException {
        return clientDAO.getAll();
    }

    public ScanningProfile createProfile(String profileName, Client client) throws SQLException {
        if (client == null) {
            throw new IllegalArgumentException("Client is required.");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Profile name cannot be empty.");
        }
        return profileDAO.create(profileName.trim(), client.getClientId(), null);
    }
}

