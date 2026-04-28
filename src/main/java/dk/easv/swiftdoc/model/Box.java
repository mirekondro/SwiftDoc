package dk.easv.swiftdoc.model;

/**
 * Represents a scanning session target — a "box" of paper to scan.
 * Maps to the dbo.Boxes table.
 *
 *   BoxId          = primary key (auto-increment)
 *   BoxName        = human label (e.g. "Inbox 001")
 *   ProfileId      = FK to Profiles, set at session start
 *   GlobalRotation = default rotation applied to every page in the box
 *                    (0, 90, 180, 270). Sprint 1 always 0.
 *
 * The DB schema has no createdBy/createdAt yet — audit info will live in
 * the SystemLogs table when US-22 is implemented.
 */
public class Box {

    private int boxId;
    private String boxName;
    private int profileId;
    private int globalRotation;

    public Box(int boxId, String boxName, int profileId, int globalRotation) {
        this.boxId = boxId;
        this.boxName = boxName;
        this.profileId = profileId;
        this.globalRotation = globalRotation;
    }

    public int getBoxId() {
        return boxId;
    }

    public void setBoxId(int boxId) {
        this.boxId = boxId;
    }

    public String getBoxName() {
        return boxName;
    }

    public void setBoxName(String boxName) {
        this.boxName = boxName;
    }

    public int getProfileId() {
        return profileId;
    }

    public void setProfileId(int profileId) {
        this.profileId = profileId;
    }

    public int getGlobalRotation() {
        return globalRotation;
    }

    public void setGlobalRotation(int globalRotation) {
        this.globalRotation = globalRotation;
    }

    @Override
    public String toString() {
        return boxName;
    }
}
