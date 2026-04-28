package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.Document;

/**
 * Live state of an in-progress scanning session.
 *
 *   box             = the Box we're scanning into (immutable for the session)
 *   firstDocument   = the very first Document, created when the session started
 *                     (immutable for the session — historical anchor)
 *   currentDocument = the Document new files attach to right now. Changes
 *                     each time a barcode separator is detected.
 *   totalFileCount  = running count of Files saved so far (US-10 counter)
 *
 * Mutability is intentional: the session is a stateful UI object, not a
 * value record. Created by {@link ScanSessionService#startSession} and
 * mutated by {@link ScanService}.
 */
public class ScanSession {

    private final Box box;
    private final Document firstDocument;
    private Document currentDocument;
    private int totalFileCount;

    public ScanSession(Box box, Document firstDocument) {
        this.box = box;
        this.firstDocument = firstDocument;
        this.currentDocument = firstDocument;
        this.totalFileCount = 0;
    }

    public Box getBox() {
        return box;
    }

    public Document getFirstDocument() {
        return firstDocument;
    }

    public Document getCurrentDocument() {
        return currentDocument;
    }

    /**
     * Move the active cursor to a new Document. Called by ScanService when
     * a barcode separator is detected and a new Document has been created.
     */
    public void setCurrentDocument(Document currentDocument) {
        if (currentDocument == null) {
            throw new IllegalArgumentException("currentDocument cannot be null");
        }
        this.currentDocument = currentDocument;
    }

    public int getTotalFileCount() {
        return totalFileCount;
    }

    /** Called by ScanService each time a File is successfully saved. */
    public void incrementFileCount() {
        this.totalFileCount++;
    }
}
