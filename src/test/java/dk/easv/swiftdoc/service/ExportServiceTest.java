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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link ExportService} end-to-end with in-memory stubs for the
 * DAO layer. Verifies the export pipeline writes real TIFF files for the
 * happy path and surfaces useful diagnostics for the edge cases that produce
 * empty folders.
 */
class ExportServiceTest {

    private static final int BOX_ID = 42;
    private static final int PROFILE_ID = 7;

    @Test
    void exportBox_writesOneTiffPerDocument(@TempDir Path tmp) throws Exception {
        byte[] tiffBytes = makeTiff(120, 80);

        Box box = new Box(BOX_ID, "Box 42", PROFILE_ID, 0);
        ScanningProfile profile = new ScanningProfile(
                PROFILE_ID, "Test Profile", null, 1, "ACME", false, 0, 0, false);

        Document doc1 = new Document(100, BOX_ID, 1, "BC-1", Document.Status.NEW);
        Document doc2 = new Document(101, BOX_ID, 2, "BC-2", Document.Status.NEW);

        Map<Integer, List<File>> filesByDoc = new HashMap<>();
        filesByDoc.put(100, List.of(
                new File(1000, 100, 1, 1, 0, null),
                new File(1001, 100, 2, 2, 0, null)));
        filesByDoc.put(101, List.of(
                new File(1002, 101, 3, 1, 0, null)));

        ExportService service = service(box, profile, List.of(doc1, doc2),
                filesByDoc, tiffBytes);

        ExportService.ExportResult result =
                service.exportBox(BOX_ID, tmp.toFile());

        assertEquals(2, result.filesWritten(), "expected one TIFF per document");
        assertEquals(3, result.pagesWritten(), "expected 2 + 1 pages total");
        assertTrue(result.skipped().isEmpty(), "no docs should be skipped");

        java.io.File outDir = new java.io.File(result.outputDir());
        assertTrue(outDir.isDirectory());
        String[] files = outDir.list();
        assertNotNull(files);
        assertEquals(2, files.length, "expected exactly 2 TIFF files");
    }

    @Test
    void exportBox_emptyBox_writesNothingAndDoesNotCreateSubfolder(@TempDir Path tmp) throws Exception {
        Box box = new Box(BOX_ID, "Empty Box", PROFILE_ID, 0);
        ScanningProfile profile = new ScanningProfile(
                PROFILE_ID, "Test Profile", null, 1, "ACME", false, 0, 0, false);

        ExportService service = service(box, profile, List.of(), Map.of(), new byte[0]);
        ExportService.ExportResult result = service.exportBox(BOX_ID, tmp.toFile());

        assertEquals(0, result.filesWritten());
        assertEquals(0, result.pagesWritten());
        assertFalse(result.skipped().isEmpty(),
                "empty box should produce a clear diagnostic in skipped");
        // The empty subfolder is no longer created — the result points at the
        // original output directory instead.
        assertEquals(tmp.toFile().getAbsolutePath(), result.outputDir());
        String[] entries = tmp.toFile().list();
        assertNotNull(entries);
        assertEquals(0, entries.length, "tmp dir should remain empty");
    }

    @Test
    void exportBox_docsWithoutFiles_areReportedAsSkippedAndNoFolderCreated(@TempDir Path tmp) throws Exception {
        Box box = new Box(BOX_ID, "Sparse Box", PROFILE_ID, 0);
        ScanningProfile profile = new ScanningProfile(
                PROFILE_ID, "Test Profile", null, 1, "ACME", false, 0, 0, false);

        Document doc = new Document(200, BOX_ID, 1, null, Document.Status.NEW);
        ExportService service = service(box, profile, List.of(doc),
                Map.of(200, List.of()), new byte[0]);

        ExportService.ExportResult result = service.exportBox(BOX_ID, tmp.toFile());

        assertEquals(0, result.filesWritten());
        assertEquals(1, result.skipped().size(),
                "the empty doc should be reported in the skipped list");
        assertTrue(result.skipped().get(0).contains("no pages"));
        // No subfolder should be created when nothing was written.
        String[] entries = tmp.toFile().list();
        assertNotNull(entries);
        assertEquals(0, entries.length);
    }

    @Test
    void exportBoxAsSinglePages_writesOneFilePerPage(@TempDir Path tmp) throws Exception {
        byte[] tiffBytes = makeTiff(50, 50);

        Box box = new Box(BOX_ID, "Box", PROFILE_ID, 0);
        ScanningProfile profile = new ScanningProfile(
                PROFILE_ID, "Test Profile", null, 1, "ACME", false, 0, 0, false);

        Document doc = new Document(300, BOX_ID, 1, "BC", Document.Status.NEW);
        Map<Integer, List<File>> filesByDoc = Map.of(300, List.of(
                new File(2000, 300, 1, 1, 0, null),
                new File(2001, 300, 2, 2, 0, null)));

        ExportService service = service(box, profile, List.of(doc), filesByDoc, tiffBytes);

        ExportService.ExportResult result =
                service.exportBoxAsSinglePages(BOX_ID, tmp.toFile());

        assertEquals(2, result.filesWritten());
        assertEquals(2, result.pagesWritten());

        java.io.File outDir = new java.io.File(result.outputDir());
        String[] files = outDir.list();
        assertNotNull(files);
        assertEquals(2, files.length);
    }

    // --- helpers ---------------------------------------------------------

    private static ExportService service(Box box, ScanningProfile profile,
                                         List<Document> documents,
                                         Map<Integer, List<File>> filesByDoc,
                                         byte[] tiffBytes) {
        BoxDAO boxDAO = new BoxDAO() {
            @Override
            public Optional<Box> getById(int boxId) {
                return boxId == box.getBoxId() ? Optional.of(box) : Optional.empty();
            }
        };
        ProfileDAO profileDAO = new ProfileDAO() {
            @Override
            public Optional<ScanningProfile> getById(int id) {
                return id == profile.getProfileId() ? Optional.of(profile) : Optional.empty();
            }
        };
        DocumentDAO documentDAO = new DocumentDAO() {
            @Override
            public List<Document> getByBox(int boxId) {
                return documents;
            }
        };
        FileDAO fileDAO = new FileDAO() {
            @Override
            public List<File> getByDocument(int documentId) {
                return filesByDoc.getOrDefault(documentId, List.of());
            }
            @Override
            public byte[] getTiffData(int fileId) {
                return tiffBytes;
            }
        };
        return new ExportService(boxDAO, profileDAO, documentDAO, fileDAO, new TiffExporter());
    }

    /** Encode a solid-blue BufferedImage to in-memory TIFF bytes. */
    private static byte[] makeTiff(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("TIFF").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionType("LZW");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), params);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
