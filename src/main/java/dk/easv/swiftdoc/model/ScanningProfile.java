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
    private boolean duplicateDetectionEnabled;

    // Processing settings — applied to every page at export time.
    private int profileRotation;      // 0..359 degrees clockwise
    private int profileBrightness;    // -100..100, 0 = no change
    private boolean blackAndWhite;    // convert to 1-bit B&W

    public ScanningProfile(int profileId, String profileName, String splitRule) {
        this(profileId, profileName, splitRule, 0, "Unknown", false, 0, 0, false);
    }

    public ScanningProfile(int profileId, String profileName, String splitRule,
                           int clientId, String clientName) {
        this(profileId, profileName, splitRule, clientId, clientName, false, 0, 0, false);
    }

    public ScanningProfile(int profileId, String profileName, String splitRule,
                           int clientId, String clientName, boolean duplicateDetectionEnabled) {
        this(profileId, profileName, splitRule, clientId, clientName,
                duplicateDetectionEnabled, 0, 0, false);
    }

    /** Full constructor including processing settings. */
    public ScanningProfile(int profileId, String profileName, String splitRule,
                           int clientId, String clientName, boolean duplicateDetectionEnabled,
                           int profileRotation, int profileBrightness, boolean blackAndWhite) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.splitRule = splitRule;
        this.clientId = clientId;
        this.clientName = clientName;
        this.duplicateDetectionEnabled = duplicateDetectionEnabled;
        this.profileRotation = profileRotation;
        this.profileBrightness = profileBrightness;
        this.blackAndWhite = blackAndWhite;
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

    public int getProfileRotation() {
        return profileRotation;
    }

    public void setProfileRotation(int profileRotation) {
        this.profileRotation = profileRotation;
    }

    public int getProfileBrightness() {
        return profileBrightness;
    }

    public void setProfileBrightness(int profileBrightness) {
        this.profileBrightness = profileBrightness;
    }

    public boolean isBlackAndWhite() {
        return blackAndWhite;
    }

    public void setBlackAndWhite(boolean blackAndWhite) {
        this.blackAndWhite = blackAndWhite;
    }


    public boolean isDuplicateDetectionEnabled() {
        return duplicateDetectionEnabled;
    }

    public void setDuplicateDetectionEnabled(boolean duplicateDetectionEnabled) {
        this.duplicateDetectionEnabled = duplicateDetectionEnabled;
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
