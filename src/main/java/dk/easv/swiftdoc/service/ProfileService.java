package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.BoxDAO;
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
    private final BoxDAO boxDAO;

    public ProfileService() {
        this(new ProfileDAO(), new ClientDAO(), new BoxDAO());
    }

    public ProfileService(ProfileDAO profileDAO, ClientDAO clientDAO, BoxDAO boxDAO) {
        this.profileDAO = profileDAO;
        this.clientDAO = clientDAO;
        this.boxDAO = boxDAO;
    }

    public List<Client> getClients() throws SQLException {
        return clientDAO.getAll();
    }

    public Client createClient(String clientName) throws SQLException {
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalArgumentException("Client name cannot be empty.");
        }
        String trimmed = clientName.trim();
        if (clientDAO.existsByName(trimmed)) {
            throw new IllegalArgumentException("A client with that name already exists.");
        }
        return clientDAO.create(trimmed);
    }

    public List<ScanningProfile> getProfiles() throws SQLException {
        return profileDAO.getAll();
    }

    public ScanningProfile createProfile(String profileName, Client client,
                                         boolean duplicateDetectionEnabled) throws SQLException {
        return createProfile(profileName, client, duplicateDetectionEnabled, 0, 0, false);
    }

    public ScanningProfile createProfile(String profileName, Client client,
                                         boolean duplicateDetectionEnabled,
                                         int profileRotation, int profileBrightness,
                                         boolean blackAndWhite) throws SQLException {
        if (client == null) {
            throw new IllegalArgumentException("Client is required.");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Profile name cannot be empty.");
        }
        // Normalize rotation to 0..359 and clamp brightness to -100..100.
        int rotation = ((profileRotation % 360) + 360) % 360;
        int brightness = Math.max(-100, Math.min(100, profileBrightness));

        return profileDAO.create(profileName.trim(), client.getClientId(), null,
                duplicateDetectionEnabled, rotation, brightness, blackAndWhite);
    }

    public void updateProfile(int profileId, String name, Client client,
                              boolean duplicateDetectionEnabled,
                              int profileRotation, int profileBrightness, boolean blackAndWhite)
            throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Profile name is required.");
        }
        if (client == null) {
            throw new IllegalArgumentException("Client is required.");
        }
        profileDAO.update(profileId, name.trim(), client.getClientId(),
                duplicateDetectionEnabled, profileRotation, profileBrightness, blackAndWhite);
    }

    public void deleteProfile(int profileId) throws SQLException {
        if (boxDAO.countByProfile(profileId) > 0) {
            throw new IllegalStateException(
                    "Cannot delete profile — it still has scanning boxes. " +
                    "Remove all boxes first.");
        }
        profileDAO.delete(profileId);
    }

    public void setProfileActive(int profileId, boolean active) throws SQLException {
        profileDAO.setActive(profileId, active);
    }

}
