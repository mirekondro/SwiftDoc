package dk.easv.swiftdoc.model;

public class ScanningProfile {
    private int profileId;
    private String profileName;
    private String description;
    private String barcodeSplitRule;
    private int createdBy;

    public ScanningProfile(int profileId, String profileName, String description, String barcodeSplitRule, int createdBy) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.description = description;
        this.barcodeSplitRule = barcodeSplitRule;
        this.createdBy = createdBy;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBarcodeSplitRule() {
        return barcodeSplitRule;
    }

    public void setBarcodeSplitRule(String barcodeSplitRule) {
        this.barcodeSplitRule = barcodeSplitRule;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return profileName;
    }
}

