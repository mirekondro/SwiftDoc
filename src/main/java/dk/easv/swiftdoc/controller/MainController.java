package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.dal.TiffImageLoader;
import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.Document;
import dk.easv.swiftdoc.model.File;
import dk.easv.swiftdoc.service.ScanService;
import dk.easv.swiftdoc.service.ScanService.ScanResult;
import dk.easv.swiftdoc.service.ScanSession;
import dk.easv.swiftdoc.service.SidebarService;
import dk.easv.swiftdoc.service.SidebarService.BoxBranch;
import dk.easv.swiftdoc.service.SidebarService.DocumentBranch;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import dk.easv.swiftdoc.service.ExportService;
import dk.easv.swiftdoc.service.ExportService.ExportResult;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainController {

    private final ScanService scanService = new ScanService();
    private final SidebarService sidebarService = new SidebarService();
    private final TiffImageLoader tiffImageLoader = new TiffImageLoader();

    private ScanSession activeSession;
    private double viewerRotationDegrees = 0.0;
    private TreeItem<SidebarNode> draggedTreeItem;

    @FXML private HBox root;
    @FXML private Button scanButton;
    @FXML private Label sessionInfoLabel;
    @FXML private Label counterLabel;
    @FXML private Label lastResultLabel;
    @FXML private Label viewerCaptionLabel;
    @FXML private ImageView pageImageView;
    @FXML private TreeView<SidebarNode> sidebarTree;

    /**
     * Wrapper for tree node values. Each node holds either a Box, a Document,
     * or a File — and renders its display text accordingly.
     */
    public record SidebarNode(Kind kind, Box box, Document document, File file) {
        public enum Kind { BOX, DOCUMENT, FILE }

        static SidebarNode forBox(Box box) {
            return new SidebarNode(Kind.BOX, box, null, null);
        }
        static SidebarNode forDocument(Document doc) {
            return new SidebarNode(Kind.DOCUMENT, null, doc, null);
        }
        static SidebarNode forFile(File file) {
            return new SidebarNode(Kind.FILE, null, null, file);
        }

        @Override
        public String toString() {
            return switch (kind) {
                case BOX -> "\uD83D\uDCC2 Box #" + box.getBoxId();
                case DOCUMENT -> "\uD83D\uDCC4 Document #" + document.getDocumentNumber()
                        + (document.getBarcodeValue() != null
                        ? " [" + document.getBarcodeValue() + "]"
                        : "");
                case FILE -> "\uD83D\uDCC3 File #" + file.getReferenceId();
            };
        }
    }

    @FXML
    private void initialize() {
        Platform.runLater(() -> root.requestFocus());

        // Sidebar tree setup — invisible root, populated below.
        sidebarTree.setRoot(new TreeItem<>(null));
        loadSidebarTree();
        configureSidebarDragAndDrop();

        sidebarTree.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldSel, newSel) -> onSidebarSelectionChanged(newSel));
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.F1) {
            onNewCommand();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.F2) {
            onScanCommand();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.F3) {
            rotateViewer(-90);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.F4) {
            rotateViewer(90);
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.R) {
            rotateViewer(90);
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.L) {
            rotateViewer(-90);
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.DIGIT0) {
            resetViewerRotation();
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.S) {
            onSaveCommand();
            event.consume();
        }
    }

    // ---------------------------------------------------------------------
    // Sidebar tree
    // ---------------------------------------------------------------------

    /**
     * Load the historical tree from the DB and populate the sidebar.
     * Runs synchronously on the FX thread for now — typical school project
     * volumes are small enough that this is fine. If the DB ever holds
     * thousands of files, move it to a background thread.
     */
    private void loadSidebarTree() {
        try {
            List<BoxBranch> branches = sidebarService.loadTree();
            TreeItem<SidebarNode> root = sidebarTree.getRoot();
            root.getChildren().clear();

            for (BoxBranch branch : branches) {
                root.getChildren().add(buildBoxItem(branch));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            sidebarTree.setRoot(new TreeItem<>(null));
            System.err.println("Could not load sidebar tree: " + ex.getMessage());
        }
    }

    private void configureSidebarDragAndDrop() {
        sidebarTree.setCellFactory(treeView -> {
            TreeCell<SidebarNode> cell = new TreeCell<>() {
                @Override
                protected void updateItem(SidebarNode item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.toString());
                    setGraphic(null);
                }
            };

            cell.setOnDragDetected(event -> {
                TreeItem<SidebarNode> item = cell.getTreeItem();
                if (!isFileItem(item)) {
                    return;
                }
                Dragboard dragboard = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(Integer.toString(item.getValue().file().getFileId()));
                dragboard.setContent(content);
                draggedTreeItem = item;
                event.consume();
            });

            cell.setOnDragOver(event -> {
                if (draggedTreeItem == null) {
                    return;
                }
                TreeItem<SidebarNode> target = cell.getTreeItem();
                if (isValidDropTarget(draggedTreeItem, target)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            cell.setOnDragDropped(event -> {
                if (draggedTreeItem == null) {
                    return;
                }
                TreeItem<SidebarNode> target = cell.getTreeItem();
                boolean completed = false;
                if (isValidDropTarget(draggedTreeItem, target)) {
                    completed = moveDraggedFile(target);
                }
                event.setDropCompleted(completed);
                event.consume();
            });

            cell.setOnDragDone(event -> draggedTreeItem = null);
            return cell;
        });
    }

    private boolean isValidDropTarget(TreeItem<SidebarNode> dragged, TreeItem<SidebarNode> target) {
        if (!isFileItem(dragged) || target == null || target == dragged) {
            return false;
        }
        if (isFileItem(target)) {
            return target.getParent() == dragged.getParent();
        }
        return isDocumentItem(target) && target == dragged.getParent();
    }

    private boolean moveDraggedFile(TreeItem<SidebarNode> target) {
        TreeItem<SidebarNode> documentItem = draggedTreeItem.getParent();
        if (!isDocumentItem(documentItem)) {
            return false;
        }

        List<TreeItem<SidebarNode>> siblings = documentItem.getChildren();
        int draggedIndex = siblings.indexOf(draggedTreeItem);
        int dropIndex = isFileItem(target) ? siblings.indexOf(target) : siblings.size();

        if (draggedIndex < 0 || dropIndex < 0) {
            return false;
        }
        if (draggedIndex < dropIndex) {
            dropIndex--;
        }
        if (draggedIndex == dropIndex) {
            return true;
        }

        siblings.remove(draggedTreeItem);
        siblings.add(dropIndex, draggedTreeItem);
        persistDocumentOrderAsync(documentItem);
        return true;
    }

    private void persistDocumentOrderAsync(TreeItem<SidebarNode> documentItem) {
        List<File> orderedFiles = new ArrayList<>();
        int incrementalId = 1;
        for (TreeItem<SidebarNode> fileItem : documentItem.getChildren()) {
            SidebarNode node = fileItem.getValue();
            if (node != null && node.kind() == SidebarNode.Kind.FILE) {
                File file = node.file();
                file.setIncrementalId(incrementalId++);
                orderedFiles.add(file);
            }
        }

        if (orderedFiles.isEmpty()) {
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                sidebarService.updateFileOrder(
                        documentItem.getValue().document().getDocumentId(),
                        orderedFiles);
            } catch (SQLException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    loadSidebarTree();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Reorder failed");
                    alert.setHeaderText("Could not save file order");
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                });
            }
        }, "sidebar-reorder-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean isFileItem(TreeItem<SidebarNode> item) {
        return item != null && item.getValue() != null
                && item.getValue().kind() == SidebarNode.Kind.FILE;
    }

    private boolean isDocumentItem(TreeItem<SidebarNode> item) {
        return item != null && item.getValue() != null
                && item.getValue().kind() == SidebarNode.Kind.DOCUMENT;
    }

    private TreeItem<SidebarNode> buildBoxItem(BoxBranch branch) {
        TreeItem<SidebarNode> boxItem = new TreeItem<>(SidebarNode.forBox(branch.box()));
        for (DocumentBranch docBranch : branch.documents()) {
            boxItem.getChildren().add(buildDocumentItem(docBranch));
        }
        boxItem.setExpanded(true);
        return boxItem;
    }

    private TreeItem<SidebarNode> buildDocumentItem(DocumentBranch docBranch) {
        TreeItem<SidebarNode> docItem = new TreeItem<>(SidebarNode.forDocument(docBranch.document()));
        for (File file : docBranch.files()) {
            docItem.getChildren().add(new TreeItem<>(SidebarNode.forFile(file)));
        }
        docItem.setExpanded(true);
        return docItem;
    }

    /**
     * Called when the user clicks a node in the tree.
     * Only File nodes trigger the viewer — clicking a Box or Document is
     * just a navigation gesture (expand/collapse).
     */
    private void onSidebarSelectionChanged(TreeItem<SidebarNode> selected) {
        if (selected == null || selected.getValue() == null) return;
        SidebarNode node = selected.getValue();
        if (node.kind() != SidebarNode.Kind.FILE) return;

        loadAndDisplayFile(node.file());
    }


    @FXML
    private void onRotateLeftCommand() {
        rotateViewer(-90);
    }

    @FXML
    private void onRotateRightCommand() {
        rotateViewer(90);
    }

    @FXML
    private void onResetRotationCommand() {
        resetViewerRotation();
    }


    /**
     * Fetch TIFF bytes for the clicked file and show in the viewer.
     * DB call runs on a background thread; UI update on FX thread.
     */
    private void loadAndDisplayFile(File file) {
        viewerCaptionLabel.setText("Loading File #" + file.getReferenceId() + "...");

        Thread worker = new Thread(() -> {
            try {
                byte[] tiffBytes = sidebarService.loadTiffData(file.getFileId());
                if (tiffBytes == null || tiffBytes.length == 0) {
                    Platform.runLater(() -> viewerCaptionLabel.setText(
                            "File #" + file.getReferenceId() + " has no data."));
                    return;
                }
                Image image = tiffImageLoader.load(tiffBytes);
                Platform.runLater(() -> {
                    pageImageView.setImage(image);
                    pageImageView.setRotate(viewerRotationDegrees);
                    viewerCaptionLabel.setText(
                            "File #" + file.getReferenceId()
                                    + " — Document " + file.getDocumentId());
                });
            } catch (SQLException | IOException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> viewerCaptionLabel.setText(
                        "Could not load File #" + file.getReferenceId()
                                + ": " + ex.getMessage()));
            }
        }, "sidebar-load-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Append a new file under its document branch in the sidebar. */
    private void addFileToSidebar(File file) {
        TreeItem<SidebarNode> docItem = findDocumentItem(file.getDocumentId());
        if (docItem != null) {
            docItem.getChildren().add(new TreeItem<>(SidebarNode.forFile(file)));
            docItem.setExpanded(true);
        }
    }

    /** Append a new (empty) document branch under its parent box. */
    private void addDocumentToSidebar(Document doc) {
        TreeItem<SidebarNode> boxItem = findBoxItem(doc.getBoxId());
        if (boxItem != null) {
            TreeItem<SidebarNode> docItem = new TreeItem<>(SidebarNode.forDocument(doc));
            docItem.setExpanded(true);
            boxItem.getChildren().add(docItem);
        }
    }

    private TreeItem<SidebarNode> findBoxItem(int boxId) {
        for (TreeItem<SidebarNode> boxItem : sidebarTree.getRoot().getChildren()) {
            SidebarNode value = boxItem.getValue();
            if (value != null && value.kind() == SidebarNode.Kind.BOX
                    && value.box().getBoxId() == boxId) {
                return boxItem;
            }
        }
        return null;
    }

    private TreeItem<SidebarNode> findDocumentItem(int documentId) {
        for (TreeItem<SidebarNode> boxItem : sidebarTree.getRoot().getChildren()) {
            for (TreeItem<SidebarNode> docItem : boxItem.getChildren()) {
                SidebarNode value = docItem.getValue();
                if (value != null && value.kind() == SidebarNode.Kind.DOCUMENT
                        && value.document().getDocumentId() == documentId) {
                    return docItem;
                }
            }
        }
        return null;
    }


    @FXML
    private void onNewCommand() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainController.class.getResource("/dk/easv/swiftdoc/view/new-scan-dialog.fxml"));
            DialogPane dialogPane = loader.load();
            NewScanDialogController dialogController = loader.getController();

            Dialog<?> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("New Scan");
            dialog.showAndWait();

            ScanSession started = dialogController.getCreatedSession();
            if (started != null) {
                this.activeSession = started;
                onSessionStarted();
            } else {
                System.out.println("New Scan cancelled — no session started.");
            }
        } catch (IOException ex) {
            System.err.println("Failed to load new scan dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void onSessionStarted() {
        scanButton.setDisable(false);
        refreshSessionLabels();
        lastResultLabel.setText("Ready. Press Scan to fetch the next page.");
        viewerCaptionLabel.setText("No page to display yet");
        pageImageView.setImage(null);

        // Add the new Box + first Document into the sidebar tree.
        TreeItem<SidebarNode> boxItem = new TreeItem<>(
                SidebarNode.forBox(activeSession.getBox()));
        TreeItem<SidebarNode> firstDocItem = new TreeItem<>(
                SidebarNode.forDocument(activeSession.getFirstDocument()));
        firstDocItem.setExpanded(true);
        boxItem.getChildren().add(firstDocItem);
        boxItem.setExpanded(true);
        sidebarTree.getRoot().getChildren().add(boxItem);

        System.out.println("Session started — Box id "
                + activeSession.getBox().getBoxId()
                + ", first Document id " + activeSession.getFirstDocument().getDocumentId());
    }

    @FXML
    private void onScanCommand() {
        if (activeSession == null) {
            return;
        }
        scanButton.setDisable(true);
        lastResultLabel.setText("Scanning...");

        Thread worker = new Thread(this::performScan, "scan-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Background thread. Must NOT touch JavaFX nodes directly. */
    private void performScan() {
        try {
            List<ScanResult> results = scanService.scan(activeSession);
            Platform.runLater(() -> applyResultsToUi(results));

        } catch (IOException ex) {
            ex.printStackTrace();
            Platform.runLater(() -> handleRetryableError(
                    "Connection problem", explainIoException(ex)));

        } catch (SQLException ex) {
            ex.printStackTrace();
            Platform.runLater(() -> handleRetryableError(
                    "Database error",
                    "Could not save the scan to the database.\n\n"
                            + "This usually means the server is unreachable or "
                            + "the schema is out of sync.\n\nDetails: " + ex.getMessage()));

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Platform.runLater(() -> {
                lastResultLabel.setText("Scan interrupted.");
                scanButton.setDisable(false);
            });

        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> handleUnexpectedError(ex));
        }
    }

    /** JavaFX thread. */
    private void applyResultsToUi(List<ScanResult> results) {
        for (ScanResult r : results) {
            lastResultLabel.setText("Page saved as File #"
                    + r.savedFile().getReferenceId()
                    + " (" + r.savedFile().getTiffData().length + " bytes)");
            System.out.println("PAGE saved: File #" + r.savedFile().getReferenceId()
                    + " in Document " + r.savedFile().getDocumentId());

            showPageInViewer(
                    r.tiffBytes(),
                    "File #" + r.savedFile().getReferenceId()
                            + " — Document " + r.savedFile().getDocumentId()
            );
            addFileToSidebar(r.savedFile());
        }

        refreshSessionLabels();
        scanButton.setDisable(false);
    }

    /**
     * Decode the TIFF bytes and show them in the viewer.
     * Failure here is non-fatal — we keep the previous image and log a note,
     * because the page is already saved/processed at this point.
     */
    private void showPageInViewer(byte[] tiffBytes, String caption) {
        try {
            Image image = tiffImageLoader.load(tiffBytes);
            pageImageView.setImage(image);
            pageImageView.setRotate(viewerRotationDegrees);
            viewerCaptionLabel.setText(caption);
        } catch (IOException ex) {
            System.err.println("Could not decode TIFF for viewer: " + ex.getMessage());
            viewerCaptionLabel.setText(caption + "  (preview unavailable)");
        }
    }

    private void rotateViewer(int deltaDegrees) {
        viewerRotationDegrees = (viewerRotationDegrees + deltaDegrees) % 360;
        if (viewerRotationDegrees < 0) {
            viewerRotationDegrees += 360;
        }
        pageImageView.setRotate(viewerRotationDegrees);
        viewerCaptionLabel.setText("Rotation: " + (int) viewerRotationDegrees + "°");
    }

    private void resetViewerRotation() {
        viewerRotationDegrees = 0;
        pageImageView.setRotate(viewerRotationDegrees);
        viewerCaptionLabel.setText("Rotation: 0°");
    }

    private void refreshSessionLabels() {
        sessionInfoLabel.setText(
                "Box #" + activeSession.getBox().getBoxId() + "\n"
                        + "Current document: #"
                        + activeSession.getCurrentDocument().getDocumentNumber());
        counterLabel.setText("Files scanned: " + activeSession.getTotalFileCount());
    }

    private void handleRetryableError(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Scan failed");
        alert.setHeaderText(header);
        alert.setContentText(body);

        ButtonType retry = new ButtonType("Retry");
        ButtonType cancel = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(retry, cancel);

        Optional<ButtonType> choice = alert.showAndWait();
        scanButton.setDisable(false);

        if (choice.isPresent() && choice.get() == retry) {
            lastResultLabel.setText("Retrying...");
            onScanCommand();
        } else {
            lastResultLabel.setText("Scan cancelled. Press Scan to try again.");
        }
    }

    private void handleUnexpectedError(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Unexpected error");
        alert.setHeaderText("Something went wrong inside the app");
        alert.setContentText(
                "This is probably a bug. Details have been printed to the console.\n\n"
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        alert.showAndWait();

        lastResultLabel.setText("Last scan failed (see console).");
        scanButton.setDisable(false);
    }

    private String explainIoException(IOException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        if (message.contains("timed out") || message.contains("timeout")) {
            return "The scanner API didn't respond in time.\n\n"
                    + "Check your internet connection and try again.";
        }
        if (message.contains("unable to find") || message.contains("unknown host")
                || message.contains("no address associated")) {
            return "Couldn't find the scanner API server.\n\n"
                    + "Check your internet connection and DNS.";
        }
        if (message.contains("connection refused") || message.contains("connect")) {
            return "Couldn't connect to the scanner API.\n\n"
                    + "The server may be down. Try again in a moment.";
        }
        if (message.contains("http")) {
            return "The scanner API returned an unexpected response.\n\n"
                    + "Details: " + ex.getMessage();
        }
        if (message.contains("imageio") || message.contains("decode")) {
            return "The downloaded page couldn't be processed.\n\n"
                    + "It may be corrupt. Skip this one and try again.";
        }
        return "Could not complete the scan.\n\nDetails: " + ex.getMessage();
    }

    private final ExportService exportService = new ExportService();

    @FXML
    private void onSaveCommand() {
        if (activeSession == null) {
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Nothing to save");
            info.setHeaderText("No active session");
            info.setContentText("Start a scan session first, then press Save to export "
                    + "the box as multi-page TIFFs.");
            info.showAndWait();
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose export folder");
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        java.io.File outputDir = chooser.showDialog(owner);
        if (outputDir == null) {
            // User cancelled the folder picker.
            return;
        }

        int boxId = activeSession.getBox().getBoxId();
        lastResultLabel.setText("Exporting box #" + boxId + "...");

        Thread worker = new Thread(() -> performExport(boxId, outputDir), "export-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void performExport(int boxId, java.io.File outputDir) {
        try {
            ExportResult result = exportService.exportBox(boxId, outputDir);
            Platform.runLater(() -> showExportResult(result));
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> {
                lastResultLabel.setText("Export failed: " + ex.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export failed");
                alert.setHeaderText("Could not export the box");
                alert.setContentText(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void showExportResult(ExportResult result) {
        StringBuilder summary = new StringBuilder();
        summary.append("Wrote ").append(result.filesWritten())
                .append(" TIFF file(s) totalling ")
                .append(result.pagesWritten()).append(" page(s)")
                .append(" to:\n").append(result.outputDir());

        if (!result.skipped().isEmpty()) {
            summary.append("\n\nSkipped:");
            for (String s : result.skipped()) {
                summary.append("\n  • ").append(s);
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export complete");
        alert.setHeaderText("Box exported successfully");
        alert.setContentText(summary.toString());
        alert.showAndWait();

        lastResultLabel.setText("Exported " + result.filesWritten() + " file(s).");
    }

    public ScanSession getActiveSession() {
        return activeSession;
    }
}
