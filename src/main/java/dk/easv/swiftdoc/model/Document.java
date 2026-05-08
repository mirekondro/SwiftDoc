package dk.easv.swiftdoc.model;

/**
 * A logical document within a box — typically the pages between two
 * barcode separators.
 *
 * Maps to the dbo.Documents table.
 *
 *   DocumentId    = primary key
 *   BoxId         = parent box
 *   BarcodeValue  = text from the separator that started this document.
 *                   NULL for the first document of a box (no separator before it).
 *
 * documentNumber (1-based order within the box) is computed at runtime, not
 * stored in the table.
 */
public class Document {

    public enum Status {
        NEW("NEW", "New"),
        IN_PROGRESS("IN_PROGRESS", "In progress"),
        ON_HOLD("ON_HOLD", "On hold"),
        DONE("DONE", "Done");

        private final String dbValue;
        private final String label;

        Status(String dbValue, String label) {
            this.dbValue = dbValue;
            this.label = label;
        }

        public String dbValue() {
            return dbValue;
        }

        public String label() {
            return label;
        }

        public static Status fromDb(String value) {
            if (value == null || value.isBlank()) {
                return NEW;
            }
            for (Status status : values()) {
                if (status.dbValue.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            return NEW;
        }
    }

    private int documentId;
    private int boxId;
    private int documentNumber;
    private String barcodeValue;
    private Status status;

    public Document(int documentId, int boxId, int documentNumber, String barcodeValue, Status status) {
        this.documentId = documentId;
        this.boxId = boxId;
        this.documentNumber = documentNumber;
        this.barcodeValue = barcodeValue;
        this.status = status == null ? Status.NEW : status;
    }

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public int getBoxId() { return boxId; }
    public void setBoxId(int boxId) { this.boxId = boxId; }

    public int getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(int documentNumber) { this.documentNumber = documentNumber; }

    public String getBarcodeValue() { return barcodeValue; }
    public void setBarcodeValue(String barcodeValue) { this.barcodeValue = barcodeValue; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status == null ? Status.NEW : status; }

    @Override
    public String toString() {
        String statusLabel = status != null ? " (" + status.label() + ")" : "";
        return "Document #" + documentNumber
                + (barcodeValue != null ? " [" + barcodeValue + "]" : "")
                + statusLabel;
    }
}
