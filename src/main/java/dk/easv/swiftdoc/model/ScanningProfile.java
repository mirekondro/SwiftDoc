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

    public ScanningProfile(int profileId, String profileName, String splitRule) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.splitRule = splitRule;
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

    /** ComboBox renders items via toString(). */
    @Override
    public String toString() {
        return profileName;
    }
}
