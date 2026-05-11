package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.BoxDAO;
import dk.easv.swiftdoc.dal.DocumentDAO;
import dk.easv.swiftdoc.dal.FileDAO;
import dk.easv.swiftdoc.dal.ProfileDAO;
import dk.easv.swiftdoc.dal.TiffExporter;
import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.Document;
import dk.easv.swiftdoc.model.File;
import dk.easv.swiftdoc.model.ScanningProfile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates exporting a Box as multi-page TIFFs (US-18).
 *
 * Flow per export call:
 *   1. Load Box, Profile, Documents, Files from the DB
 *   2. For each non-empty Document:
 *      a. Decode every File's TIFF bytes to a BufferedImage
 *      b. Apply the File's RotationAngle
 *      c. Hand the rotated images to TiffExporter
 *      d. Filename = {profileName}_{boxId}_{docNumber}.tiff
 *   3. Return a summary with counts and any skipped/failed documents
 *
 * Does NOT touch the UI. Throws on fatal errors; returns a structured
 * result for partial success (e.g. one doc skipped because empty).
 */
public class ExportService {

    private final BoxDAO boxDAO;
    private final ProfileDAO profileDAO;
    private final DocumentDAO documentDAO;
    private final FileDAO fileDAO;
    private final TiffExporter tiffExporter;

    public ExportService() {
        this(new BoxDAO(), new ProfileDAO(), new DocumentDAO(), new FileDAO(), new TiffExporter());
    }

    public ExportService(BoxDAO boxDAO, ProfileDAO profileDAO, DocumentDAO documentDAO,
                         FileDAO fileDAO, TiffExporter tiffExporter) {
        this.boxDAO = boxDAO;
        this.profileDAO = profileDAO;
        this.documentDAO = documentDAO;
        this.fileDAO = fileDAO;
        this.tiffExporter = tiffExporter;
    }

    /**
     * Result summary returned to the controller for display.
     *
     *   filesWritten = number of TIFF files actually produced
     *   pagesWritten = total pages across all files
     *   skipped      = documents that had no files (were not exported)
     *   outputDir    = absolute path of the folder we wrote to
     *   exportedDocumentIds = document ids that produced a TIFF file
     */
    public record ExportResult(int filesWritten, int pagesWritten,
                               List<String> skipped, String outputDir,
                               List<Integer> exportedDocumentIds) {}

    /**
     * Export a box: every Document becomes one multi-page TIFF.
     *
     * @param boxId        the box to export
     * @param outputDir    where to write the TIFFs (must exist and be writable)
     * @return summary of what got written
     * @throws SQLException     on DB errors
     * @throws IOException      on file-write or image-decode errors
     * @throws IllegalArgumentException if the box doesn't exist
     */
    public ExportResult exportBox(int boxId, java.io.File outputDir) throws SQLException, IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }
        if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException("outputDir must be an existing directory: " + outputDir);
        }

        Box box = boxDAO.getById(boxId).orElseThrow(
                () -> new IllegalArgumentException("Box id " + boxId + " not found"));
        ScanningProfile profile = profileDAO.getById(box.getProfileId()).orElseThrow(
                () -> new IllegalArgumentException("Profile id " + box.getProfileId() + " not found"));
        List<Document> documents = documentDAO.getByBox(boxId);
        java.io.File targetDir = resolveOutputSubfolder(outputDir, profile, boxId);

        int filesWritten = 0;
        int pagesWritten = 0;
        List<String> skipped = new ArrayList<>();
        List<Integer> exportedDocumentIds = new ArrayList<>();

        for (Document doc : documents) {
            List<File> files = fileDAO.getByDocument(doc.getDocumentId());

            if (files.isEmpty()) {
                skipped.add("Document #" + doc.getDocumentNumber() + " (no pages)");
                continue;
            }

            // Decode + rotate each file into a BufferedImage.
            List<BufferedImage> pages = new ArrayList<>(files.size());
            for (File file : files) {
                byte[] tiff = fileDAO.getTiffData(file.getFileId());
                if (tiff == null || tiff.length == 0) {
                    // Shouldn't happen if the row was inserted properly, but
                    // skip silently rather than crashing the whole export.
                    continue;
                }
                BufferedImage decoded = decode(tiff);
                int combinedRotation = file.getRotationAngle() + box.getGlobalRotation();
                BufferedImage rotated = rotate(decoded, combinedRotation);
                pages.add(rotated);
            }

            if (pages.isEmpty()) {
                skipped.add("Document #" + doc.getDocumentNumber() + " (all files unreadable)");
                continue;
            }

            String fileName = buildFileName(profile.getProfileName(),
                    box.getBoxId(), doc.getDocumentNumber());
            java.io.File outputFile = new java.io.File(targetDir, fileName);


            tiffExporter.writeMultiPage(pages, outputFile);
            filesWritten++;
            pagesWritten += pages.size();
            exportedDocumentIds.add(doc.getDocumentId());
        }

        return new ExportResult(filesWritten, pagesWritten, skipped,
                targetDir.getAbsolutePath(), exportedDocumentIds);
    }

    /**
     * Decode TIFF bytes to a BufferedImage. Distinct from TiffImageLoader
     * (which targets JavaFX Image) — exports need AWT BufferedImage.
     */
    private BufferedImage decode(byte[] tiffBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(tiffBytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                throw new IOException("ImageIO could not decode TIFF bytes");
            }
            return image;
        }
    }

    /**
     * Rotate a BufferedImage by 0/90/180/270 degrees clockwise.
     * 0 returns the original. Other values build a new rotated image.
     */
    private BufferedImage rotate(BufferedImage source, int angleDegrees) {
        int normalized = ((angleDegrees % 360) + 360) % 360;
        if (normalized == 0) return source;

        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int dstW = (normalized == 180) ? srcW : srcH;
        int dstH = (normalized == 180) ? srcH : srcW;

        BufferedImage rotated = new BufferedImage(dstW, dstH, source.getType() == 0
                ? BufferedImage.TYPE_INT_ARGB : source.getType());
        Graphics2D g = rotated.createGraphics();
        try {
            AffineTransform tx = new AffineTransform();
            tx.translate(dstW / 2.0, dstH / 2.0);
            tx.rotate(Math.toRadians(normalized));
            tx.translate(-srcW / 2.0, -srcH / 2.0);
            g.drawImage(source, tx, null);
        } finally {
            g.dispose();
        }
        return rotated;
    }

    /**
     * Build a filesystem-safe filename in the spec's format:
     *   {profileName}_{boxId}_{docNumber}.tiff
     * with non-alphanumeric characters in profileName replaced by '_'.
     */
    private String buildFileName(String profileName, int boxId, int documentNumber) {
        return sanitizeForFilesystem(profileName)
                + "_" + boxId + "_" + documentNumber + ".tiff";
    }
    public ExportResult exportBoxAsSinglePages(int boxId, java.io.File outputDir)
            throws SQLException, IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }
        if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException(
                    "outputDir must be an existing directory: " + outputDir);
        }

        Box box = boxDAO.getById(boxId).orElseThrow(
                () -> new IllegalArgumentException("Box id " + boxId + " not found"));
        ScanningProfile profile = profileDAO.getById(box.getProfileId()).orElseThrow(
                () -> new IllegalArgumentException("Profile id " + box.getProfileId() + " not found"));
        List<Document> documents = documentDAO.getByBox(boxId);

        java.io.File targetDir = resolveOutputSubfolder(outputDir, profile, boxId);
        int filesWritten = 0;
        int pagesWritten = 0;
        List<String> skipped = new ArrayList<>();
        List<Integer> exportedDocumentIds = new ArrayList<>();

        for (Document doc : documents) {
            List<File> files = fileDAO.getByDocument(doc.getDocumentId());

            if (files.isEmpty()) {
                skipped.add("Document #" + doc.getDocumentNumber() + " (no pages)");
                continue;
            }

            boolean anyExportedForThisDoc = false;
            int pageNumber = 0;

            for (File file : files) {
                pageNumber++;

                byte[] tiff = fileDAO.getTiffData(file.getFileId());
                if (tiff == null || tiff.length == 0) {
                    skipped.add("Document #" + doc.getDocumentNumber()
                            + " page " + pageNumber + " (no data)");
                    continue;
                }

                BufferedImage decoded = decode(tiff);
                int combinedRotation = file.getRotationAngle() + box.getGlobalRotation();
                BufferedImage rotated = rotate(decoded, combinedRotation);

                String fileName = buildSinglePageFileName(
                        profile.getProfileName(),
                        box.getBoxId(),
                        doc.getDocumentNumber(),
                        pageNumber);
                java.io.File outputFile = new java.io.File(targetDir, fileName);

                tiffExporter.writeMultiPage(List.of(rotated), outputFile);
                filesWritten++;
                pagesWritten++;
                anyExportedForThisDoc = true;
            }

            if (anyExportedForThisDoc) {
                exportedDocumentIds.add(doc.getDocumentId());
            }
        }

        return new ExportResult(filesWritten, pagesWritten, skipped,
                targetDir.getAbsolutePath(), exportedDocumentIds);
    }

    private java.io.File resolveOutputSubfolder(java.io.File outputDir,
                                                ScanningProfile profile,
                                                int boxId) throws IOException {
        String safeName = sanitizeForFilesystem(profile.getProfileName());
        String subfolderName = safeName + "_" + boxId;
        java.io.File subfolder = new java.io.File(outputDir, subfolderName);
        if (!subfolder.exists()) {
            if (!subfolder.mkdirs()) {
                throw new IOException(
                        "Could not create export subfolder: " + subfolder.getAbsolutePath());
            }
        } else if (!subfolder.isDirectory()) {
            throw new IOException("Export path exists but is not a directory: "
                    + subfolder.getAbsolutePath());
        }
        return subfolder;
    }


    private String sanitizeForFilesystem(String name) {
        if (name == null || name.isBlank()) {
            return "profile";
        }
        return name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }


    /**
     * Build a filesystem-safe filename for a single-page export:
     *   {profileName}_{boxId}_{docNumber}_{pageNumber}.tiff
     */
    private String buildSinglePageFileName(String profileName, int boxId,
                                           int documentNumber, int pageNumber) {
        return sanitizeForFilesystem(profileName)
                + "_" + boxId + "_" + documentNumber + "_" + pageNumber + ".tiff";
    }

}
