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

    private int documentId;
    private int boxId;
    private int documentNumber;
    private String barcodeValue;

    public Document(int documentId, int boxId, int documentNumber, String barcodeValue) {
        this.documentId = documentId;
        this.boxId = boxId;
        this.documentNumber = documentNumber;
        this.barcodeValue = barcodeValue;
    }

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public int getBoxId() { return boxId; }
    public void setBoxId(int boxId) { this.boxId = boxId; }

    public int getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(int documentNumber) { this.documentNumber = documentNumber; }

    public String getBarcodeValue() { return barcodeValue; }
    public void setBarcodeValue(String barcodeValue) { this.barcodeValue = barcodeValue; }

    @Override
    public String toString() {
        return "Document #" + documentNumber
                + (barcodeValue != null ? " [" + barcodeValue + "]" : "");
    }
}
