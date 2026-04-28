package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.BoxDAO;
import dk.easv.swiftdoc.dal.DocumentDAO;
import dk.easv.swiftdoc.dal.ProfileDAO;
import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.Document;
import dk.easv.swiftdoc.model.ScanningProfile;

import java.sql.SQLException;
import java.util.List;

/**
 * Business logic for starting a new scanning session (US-08).
 *
 * Responsibilities:
 *  - Provide the list of available profiles to the New Scan dialog.
 *  - Validate user input (profile selected, box name not blank).
 *  - Create the Box AND the first empty Document so files have somewhere to land.
 */
public class ScanSessionService {

    private final ProfileDAO profileDAO;
    private final BoxDAO boxDAO;
    private final DocumentDAO documentDAO;

    public ScanSessionService() {
        this(new ProfileDAO(), new BoxDAO(), new DocumentDAO());
    }

    /** Constructor for testing — allows injecting fakes. */
    public ScanSessionService(ProfileDAO profileDAO, BoxDAO boxDAO, DocumentDAO documentDAO) {
        this.profileDAO = profileDAO;
        this.boxDAO = boxDAO;
        this.documentDAO = documentDAO;
    }

    public List<ScanningProfile> getAvailableProfiles() throws SQLException {
        return profileDAO.getAll();
    }

    /**
     * Start a new scanning session by creating a Box and its first Document.
     */
    public ScanSession startSession(ScanningProfile profile, String boxName) throws SQLException {
        if (profile == null) {
            throw new IllegalArgumentException("A scanning profile must be selected.");
        }
        if (boxName == null || boxName.isBlank()) {
            throw new IllegalArgumentException("Box name cannot be empty.");
        }

        Box box = boxDAO.create(boxName.trim(), profile.getProfileId());
        Document firstDocument = documentDAO.create(box.getBoxId(), null);
        return new ScanSession(box, firstDocument);
    }
}
