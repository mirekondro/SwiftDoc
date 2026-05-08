package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.FileDAO;
import dk.easv.swiftdoc.dal.ScanApiClient;
import dk.easv.swiftdoc.dal.TiffBatchUnzipper;
import dk.easv.swiftdoc.dal.TiffBatchUnzipper.TiffEntry;
import dk.easv.swiftdoc.model.File;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a single "scan" action (US-09 task 4).
 *
 * Flow per call:
 *   1. Fetch random batch from API           (ScanApiClient)
 *   2. Unzip the batch                       (TiffBatchUnzipper)
 *   3. For each TIFF in the batch:
 *        a. Save TIFF as File in current Document
 */
public class ScanService {

    private final ScanApiClient apiClient;
    private final TiffBatchUnzipper unzipper;
    private final FileDAO fileDAO;

    public ScanService() {
        this(new ScanApiClient(), new TiffBatchUnzipper(), new FileDAO());
    }

    /** Constructor for testing — inject fakes. */
    public ScanService(ScanApiClient apiClient,
                       TiffBatchUnzipper unzipper,
                       FileDAO fileDAO) {
        this.apiClient = apiClient;
        this.unzipper = unzipper;
        this.fileDAO = fileDAO;
    }

    public enum Kind {
        /** A regular page was scanned and saved as a File. */
        PAGE
    }

    /**
     * One processed TIFF from a scan call.
     *
     *   kind          = PAGE
     *   tiffBytes     = the raw TIFF bytes that came back from the API.
     *   savedFile     = non-null for PAGE
     */
    public record ScanResult(
            Kind kind,
            byte[] tiffBytes,
            File savedFile) {}

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
        File saved = fileDAO.create(
                session.getCurrentDocument().getDocumentId(),
                session.getBox().getBoxId(),
                tiff.data()
        );
        session.incrementFileCount();
        return new ScanResult(Kind.PAGE, tiff.data(), saved);
    }
}
