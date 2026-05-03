package dk.easv.swiftdoc.model;

/**
 * Represents a scanning profile (one "client").
 * Maps to the dbo.Profiles table.
 *
 *   ProfileId   = primary key
 *   ProfileName = display name (used in dropdown and export filenames)
 *   SplitRule   = description of how documents are split for this profile
 *                 (e.g. "Barcode CODE128 means new document"). Free-text in
 *                 Sprint 1; could become structured config later.
 */
public class ScanningProfile {

    private int profileId;
    private String profileName;
    private String splitRule;
    private int clientId;
    private String clientName;

    public ScanningProfile(int profileId, String profileName, String splitRule) {
        this(profileId, profileName, splitRule, 0, "Unknown");
    }

    public ScanningProfile(int profileId, String profileName, String splitRule, int clientId, String clientName) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.splitRule = splitRule;
        this.clientId = clientId;
        this.clientName = clientName;
    }

    public int getProfileId() {
        return profileId;
    }

    public void setProfileId(int profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getSplitRule() {
        return splitRule;
    }

    public void setSplitRule(String splitRule) {
        this.splitRule = splitRule;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /** ComboBox renders items via toString(). */
    @Override
    public String toString() {
        if (clientName == null || clientName.isBlank()) {
            return profileName;
        }
        return clientName + " — " + profileName;
    }
}
