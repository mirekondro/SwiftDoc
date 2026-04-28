package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.BarcodeDetector;
import dk.easv.swiftdoc.dal.DocumentDAO;
import dk.easv.swiftdoc.dal.FileDAO;
import dk.easv.swiftdoc.dal.ScanApiClient;
import dk.easv.swiftdoc.dal.TiffBatchUnzipper;
import dk.easv.swiftdoc.dal.TiffBatchUnzipper.TiffEntry;
import dk.easv.swiftdoc.model.Document;
import dk.easv.swiftdoc.model.File;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates a single "scan" action (US-09 task 4).
 *
 * Flow per call:
 *   1. Fetch random batch from API           (ScanApiClient)
 *   2. Unzip the batch                       (TiffBatchUnzipper)
 *   3. For each TIFF in the batch:
 *        a. Run barcode detection            (BarcodeDetector)
 *        b. If barcode found:
 *             - Create a new Document with the barcode value
 *             - Update ScanSession.currentDocument to point at it
 *             - Skip saving the separator as a File (it's not a page)
 *        c. If no barcode:
 *             - Save the TIFF as a File in the current Document
 *             - Increment ScanSession.totalFileCount
 *
 * Returns a list of {@link ScanResult} — one per TIFF processed — so the
 * UI can update incrementally (show new pages, announce new documents).
 *
 * Today the API only ever returns 1 TIFF per batch, but the code supports
 * multi-TIFF responses so we don't have to rewrite if that changes.
 */
public class ScanService {

    private final ScanApiClient apiClient;
    private final TiffBatchUnzipper unzipper;
    private final BarcodeDetector detector;
    private final DocumentDAO documentDAO;
    private final FileDAO fileDAO;

    public ScanService() {
        this(new ScanApiClient(), new TiffBatchUnzipper(), new BarcodeDetector(),
                new DocumentDAO(), new FileDAO());
    }

    /** Constructor for testing — inject fakes. */
    public ScanService(ScanApiClient apiClient,
                       TiffBatchUnzipper unzipper,
                       BarcodeDetector detector,
                       DocumentDAO documentDAO,
                       FileDAO fileDAO) {
        this.apiClient = apiClient;
        this.unzipper = unzipper;
        this.detector = detector;
        this.documentDAO = documentDAO;
        this.fileDAO = fileDAO;
    }

    /**
     * The kind of thing we got back from one TIFF in the batch.
     */
    public enum Kind {
        /** A regular page was scanned and saved as a File. */
        PAGE,
        /** A separator was detected; a new Document was started. */
        DOCUMENT_SPLIT
    }

    /**
     * One processed TIFF from a scan call.
     *
     *   kind          = PAGE or DOCUMENT_SPLIT
     *   savedFile     = non-null when kind == PAGE; null otherwise
     *   newDocument   = non-null when kind == DOCUMENT_SPLIT; null otherwise
     *   barcodeValue  = decoded barcode text when kind == DOCUMENT_SPLIT; null otherwise
     */
    public record ScanResult(Kind kind, File savedFile, Document newDocument, String barcodeValue) {}

    /**
     * Perform one scan action. May produce zero or more results depending on
     * how many TIFFs the API returns in this batch.
     *
     * @param session the active session — mutated as files are saved and
     *                documents are split
     * @return the list of results from this scan call (one per TIFF processed)
     * @throws IOException          on network or image-decoding failure
     * @throws InterruptedException if the calling thread is interrupted
     * @throws SQLException         on DB errors
     */
    public List<ScanResult> scan(ScanSession session) throws IOException, InterruptedException, SQLException {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }

        byte[] zipBytes = apiClient.fetchRandomBatch();
        List<TiffEntry> tiffs = unzipper.extractTiffs(zipBytes);

        java.util.ArrayList<ScanResult> results = new java.util.ArrayList<>();
        for (TiffEntry tiff : tiffs) {
            results.add(processOne(session, tiff));
        }
        return results;
    }

    private ScanResult processOne(ScanSession session, TiffEntry tiff) throws IOException, SQLException {
        Optional<String> barcode = detector.detectBarcode(tiff.data());

        if (barcode.isPresent()) {
            // Separator: close current doc, start new one. Do NOT save as a File.
            String barcodeValue = barcode.get();
            Document newDoc = documentDAO.create(session.getBox().getBoxId(), barcodeValue);
            session.setCurrentDocument(newDoc);
            return new ScanResult(Kind.DOCUMENT_SPLIT, null, newDoc, barcodeValue);
        }

        // Regular page: save as File in current Document.
        File saved = fileDAO.create(
                session.getCurrentDocument().getDocumentId(),
                session.getBox().getBoxId(),
                tiff.data()
        );
        session.incrementFileCount();
        return new ScanResult(Kind.PAGE, saved, null, null);
    }
}
