package dk.easv.swiftdoc.model;

import java.time.LocalDateTime;

/**
 * Represents a scanning session target — a "box" of paper to scan.
 * Maps to the boxes table.
 *
 * Created via {@link dk.easv.swiftdoc.service.ScanSessionService#startSession}.
 */
public class Box {

    private int boxId;
    private String boxName;
    private int profileId;
    private int createdBy;
    private LocalDateTime createdAt;

    public Box(int boxId, String boxName, int profileId, int createdBy, LocalDateTime createdAt) {
        this.boxId = boxId;
        this.boxName = boxName;
        this.profileId = profileId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
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

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return boxName;
    }
}
