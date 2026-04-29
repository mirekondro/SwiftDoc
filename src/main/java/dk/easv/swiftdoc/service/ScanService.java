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
import java.util.ArrayList;
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
 *        b. If barcode found → split documents, do NOT save as File
 *        c. If no barcode → save TIFF as File in current Document
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
     *   tiffBytes     = the raw TIFF bytes that came back from the API.
     *                   Always non-null. The UI uses this to display the page,
     *                   even for separators (so the user sees the pink page
     *                   that triggered a split).
     *   savedFile     = non-null when kind == PAGE; null otherwise
     *   newDocument   = non-null when kind == DOCUMENT_SPLIT; null otherwise
     *   barcodeValue  = decoded barcode text when kind == DOCUMENT_SPLIT; null otherwise
     */
    public record ScanResult(
            Kind kind,
            byte[] tiffBytes,
            File savedFile,
            Document newDocument,
            String barcodeValue) {}

    public List<ScanResult> scan(ScanSession session) throws IOException, InterruptedException, SQLException {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }

        byte[] zipBytes = apiClient.fetchRandomBatch();
        List<TiffEntry> tiffs = unzipper.extractTiffs(zipBytes);

        List<ScanResult> results = new ArrayList<>();
        for (TiffEntry tiff : tiffs) {
            results.add(processOne(session, tiff));
        }
        return results;
    }

    private ScanResult processOne(ScanSession session, TiffEntry tiff) throws IOException, SQLException {
        Optional<String> barcode = detector.detectBarcode(tiff.data());

        if (barcode.isPresent()) {
            String barcodeValue = barcode.get();
            Document newDoc = documentDAO.create(session.getBox().getBoxId(), barcodeValue);
            session.setCurrentDocument(newDoc);
            return new ScanResult(Kind.DOCUMENT_SPLIT, tiff.data(), null, newDoc, barcodeValue);
        }

        File saved = fileDAO.create(
                session.getCurrentDocument().getDocumentId(),
                session.getBox().getBoxId(),
                tiff.data()
        );
        session.incrementFileCount();
        return new ScanResult(Kind.PAGE, tiff.data(), saved, null, null);
    }
}
