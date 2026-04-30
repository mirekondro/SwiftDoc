package dk.easv.swiftdoc.model;

/**
 * One scanned page (a single TIFF) within a document.
 * Maps to the dbo.Files table.
 *
 *   FileId        = primary key
 *   DocumentId    = parent document
 *   ReferenceId   = scan order within the box (immutable)
 *   IncrementalId = display/sort order within the document (mutable, US-15)
 *   RotationAngle = degrees clockwise (0, 90, 180, 270)
 *   TiffData      = raw TIFF bytes (added via migration)
 *
 * Sprint 1 keeps ReferenceId and IncrementalId equal. US-15 lets the user
 * drag files around, which only changes IncrementalId — the ReferenceId
 * remains an audit trail of original scan order.
 */
public class File {

    private int fileId;
    private int documentId;
    private int referenceId;
    private int incrementalId;
    private int rotationAngle;
    private byte[] tiffData;

    public File(int fileId, int documentId, int referenceId, int incrementalId,
                int rotationAngle, byte[] tiffData) {
        this.fileId = fileId;
        this.documentId = documentId;
        this.referenceId = referenceId;
        this.incrementalId = incrementalId;
        this.rotationAngle = rotationAngle;
        this.tiffData = tiffData;
    }

    public int getFileId() { return fileId; }
    public void setFileId(int fileId) { this.fileId = fileId; }

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public int getReferenceId() { return referenceId; }
    public void setReferenceId(int referenceId) { this.referenceId = referenceId; }

    public int getIncrementalId() { return incrementalId; }
    public void setIncrementalId(int incrementalId) { this.incrementalId = incrementalId; }

    public int getRotationAngle() { return rotationAngle; }
    public void setRotationAngle(int rotationAngle) { this.rotationAngle = rotationAngle; }

    public byte[] getTiffData() { return tiffData; }
    public void setTiffData(byte[] tiffData) { this.tiffData = tiffData; }

    @Override
    public String toString() {
        return "File #" + referenceId
                + " (" + (tiffData != null ? tiffData.length : 0) + " bytes)";
    }
}
