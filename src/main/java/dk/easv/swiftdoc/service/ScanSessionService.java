package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.BoxDAO;
import dk.easv.swiftdoc.dal.ProfileDAO;
import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.ScanningProfile;

import java.sql.SQLException;
import java.util.List;

/**
 * Business logic for starting a new scanning session (US-08).
 *
 * Responsibilities:
 *  - Provide the list of available profiles to the New Scan dialog.
 *  - Validate user input (profile selected, box name not blank).
 *  - Create the Box row via BoxDAO.
 *
 * Stays narrowly focused on session START. Subsequent stories
 * (US-09 fetch files, US-10 counter, etc.) belong to other services.
 */
public class ScanSessionService {

    /**
     * Sprint 1: hardcoded devuser id. Replaced in Sprint 3 by the
     * logged-in user from the Session singleton (US-04).
     */
    private static final int CURRENT_USER_ID = 1;

    private final ProfileDAO profileDAO;
    private final BoxDAO boxDAO;

    public ScanSessionService() {
        this(new ProfileDAO(), new BoxDAO());
    }

    /** Constructor for testing — allows injecting fakes. */
    public ScanSessionService(ProfileDAO profileDAO, BoxDAO boxDAO) {
        this.profileDAO = profileDAO;
        this.boxDAO = boxDAO;
    }

    /**
     * @return profiles available for the New Scan dropdown.
     *         Sprint 1: returns all profiles (no per-user filtering yet).
     *         Sprint 3 (US-06): filter by current user's access.
     */
    public List<ScanningProfile> getAvailableProfiles() throws SQLException {
        return profileDAO.getAll();
    }

    /**
     * Start a new scanning session by creating a Box.
     *
     * @param profile       the profile chosen by the user (required)
     * @param boxName       human-entered box label (required, non-blank)
     * @return the newly created Box, with DB-assigned id and timestamp
     * @throws IllegalArgumentException if profile is null or boxName is blank
     * @throws SQLException             on DB errors
     */
    public Box startSession(ScanningProfile profile, String boxName) throws SQLException {
        if (profile == null) {
            throw new IllegalArgumentException("A scanning profile must be selected.");
        }
        if (boxName == null || boxName.isBlank()) {
            throw new IllegalArgumentException("Box name cannot be empty.");
        }
        return boxDAO.create(boxName.trim(), profile.getProfileId(), CURRENT_USER_ID);
    }
}
