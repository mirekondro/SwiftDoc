package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.BoxDAO;
import dk.easv.swiftdoc.dal.DocumentDAO;
import dk.easv.swiftdoc.dal.FileDAO;
import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.Document;
import dk.easv.swiftdoc.model.File;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that backs the historical sidebar (US-14).
 *
 * Two responsibilities:
 *  1. Build the full Box → Document → File tree on demand (metadata only).
 *  2. Fetch TIFF bytes for a specific file when the user clicks on it.
 *
 * Lazy loading by design: the tree never holds TIFF bytes, only ids and
 * names. The viewer pulls bytes one file at a time. This keeps the UI
 * snappy even when the database is large.
 */
public class SidebarService {

    private final BoxDAO boxDAO;
    private final DocumentDAO documentDAO;
    private final FileDAO fileDAO;

    public SidebarService() {
        this(new BoxDAO(), new DocumentDAO(), new FileDAO());
    }

    public SidebarService(BoxDAO boxDAO, DocumentDAO documentDAO, FileDAO fileDAO) {
        this.boxDAO = boxDAO;
        this.documentDAO = documentDAO;
        this.fileDAO = fileDAO;
    }

    /**
     * One node in the loaded tree: a box and its child documents
     * (and each document its child files, no TIFF data).
     */
    public record BoxBranch(Box box, List<DocumentBranch> documents) {}
    public record DocumentBranch(Document document, List<File> files) {}

    /**
     * Build the entire historical tree from the database.
     *
     * Performance note: this fires N+1 queries (one for boxes, one per box
     * for documents, one per document for files). Fine for school-project
     * volumes; could be replaced with a single JOIN query later if needed.
     */
    public List<BoxBranch> loadTree() throws SQLException {
        List<Box> boxes = boxDAO.getAll();
        List<BoxBranch> branches = new ArrayList<>(boxes.size());

        for (Box box : boxes) {
            List<Document> documents = documentDAO.getByBox(box.getBoxId());
            List<DocumentBranch> docBranches = new ArrayList<>(documents.size());

            for (Document doc : documents) {
                List<File> files = fileDAO.getByDocument(doc.getDocumentId());
                docBranches.add(new DocumentBranch(doc, files));
            }
            branches.add(new BoxBranch(box, docBranches));
        }
        return branches;
    }

    /**
     * Fetch the TIFF bytes for a single file. Called when the user clicks
     * a file in the sidebar.
     */
    public byte[] loadTiffData(int fileId) throws SQLException {
        return fileDAO.getTiffData(fileId);
    }
}
