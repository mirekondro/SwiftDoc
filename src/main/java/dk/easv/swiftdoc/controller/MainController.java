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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
import javafx.scene.layout.VBox;
import dk.easv.swiftdoc.service.ExportService;
import dk.easv.swiftdoc.service.ExportService.ExportResult;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MainController {

    private final ScanService scanService = new ScanService();
    private final SidebarService sidebarService = new SidebarService();
    private final TiffImageLoader tiffImageLoader = new TiffImageLoader();

    private ScanSession activeSession;
    private double viewerRotationDegrees = 0.0;
    private TreeItem<SidebarNode> draggedTreeItem;

    @FXML private VBox root;
    @FXML private Button scanButton;
    @FXML private Label sessionInfoLabel;
    @FXML private Label counterLabel;
    @FXML private Label lastResultLabel;
    @FXML private Label viewerCaptionLabel;
    @FXML private ImageView pageImageView;
    @FXML private TreeView<SidebarNode> sidebarTree;
    @FXML private ToggleButton themeToggle;

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
                case DOCUMENT -> "\uD83D\uDCC4 " + document.toString();
                case FILE -> "\uD83D\uDCC3 File #" + file.getReferenceId();
            };
        }
    }

    @FXML
    private void initialize() {
        Platform.runLater(() -> root.requestFocus());

        // Sidebar tree setup — invisible root, populated below.
        sidebarTree.setRoot(new TreeItem<>(null));
        loadSidebarTreeAsync();
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
            applySidebarTree(branches);
        } catch (SQLException ex) {
            ex.printStackTrace();
            sidebarTree.setRoot(new TreeItem<>(null));
            System.err.println("Could not load sidebar tree: " + ex.getMessage());
        }
    }

    private void loadSidebarTreeAsync() {
        if (lastResultLabel != null
                && (lastResultLabel.getText() == null || lastResultLabel.getText().isBlank())) {
            lastResultLabel.setText("Loading sidebar...");
        }
        Thread worker = new Thread(() -> {
            try {
                List<BoxBranch> branches = sidebarService.loadTree();
                Platform.runLater(() -> applySidebarTree(branches));
            } catch (SQLException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    sidebarTree.setRoot(new TreeItem<>(null));
                    System.err.println("Could not load sidebar tree: " + ex.getMessage());
                });
            }
        }, "sidebar-load-tree");
        worker.setDaemon(true);
        worker.start();
    }

    private void applySidebarTree(List<BoxBranch> branches) {
        TreeItem<SidebarNode> root = sidebarTree.getRoot();
        root.getChildren().clear();

        for (BoxBranch branch : branches) {
            root.getChildren().add(buildBoxItem(branch));
        }
    }

    private void configureSidebarDragAndDrop() {
        sidebarTree.setCellFactory(treeView -> {
            TreeCell<SidebarNode> cell = new TreeCell<>() {
                private final Label textLabel = new Label();
                private final Label badgeLabel = new Label();
                private final HBox container = new HBox(6, textLabel, badgeLabel);

                @Override
                protected void updateItem(SidebarNode item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                        return;
                    }
                    container.getStyleClass().setAll("sidebar-cell");
                    textLabel.getStyleClass().setAll("sidebar-cell-text");
                    textLabel.setText(item.toString());
                    RenderStatus status = getRenderStatus(item, getTreeItem());
                    applyBadge(badgeLabel, status);
                    setText(null);
                    setGraphic(container);
                    setContextMenu(buildContextMenu(item));
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

    private ContextMenu buildContextMenu(SidebarNode node) {
        if (node == null || node.kind() != SidebarNode.Kind.DOCUMENT) {
            return null;
        }
        Document doc = node.document();
        if (doc == null) {
            return null;
        }
        ContextMenu menu = new ContextMenu();
        Menu statusMenu = new Menu("Status");
        ToggleGroup group = new ToggleGroup();
        for (Document.Status status : Document.Status.values()) {
            RadioMenuItem item = new RadioMenuItem(status.label());
            item.setToggleGroup(group);
            item.setSelected(status == doc.getStatus());
            item.setOnAction(event -> updateDocumentStatus(doc, status));
            statusMenu.getItems().add(item);
        }
        menu.getItems().add(statusMenu);
        return menu;
    }

    private void updateDocumentStatus(Document doc, Document.Status status) {
        if (doc == null || status == null || status == doc.getStatus()) {
            return;
        }
        Document.Status previous = doc.getStatus();
        doc.setStatus(status);
        sidebarTree.refresh();

        Thread worker = new Thread(() -> {
            try {
                sidebarService.updateDocumentStatus(doc.getDocumentId(), status);
            } catch (SQLException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    doc.setStatus(previous);
                    sidebarTree.refresh();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Status update failed");
                    alert.setHeaderText("Could not update document status");
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                });
            }
        }, "sidebar-update-status");
        worker.setDaemon(true);
        worker.start();
    }

    private enum RenderStatus {
        RENDERED("Rendered", "status-rendered"),
        NOT_RENDERED("Not rendered", "status-pending");

        private final String label;
        private final String styleClass;

        RenderStatus(String label, String styleClass) {
            this.label = label;
            this.styleClass = styleClass;
        }
    }

    private RenderStatus getRenderStatus(SidebarNode node, TreeItem<SidebarNode> treeItem) {
        if (node == null || node.kind() == null) {
            return RenderStatus.NOT_RENDERED;
        }
        return switch (node.kind()) {
            case FILE -> renderedDocumentIds.contains(node.file().getDocumentId())
                    ? RenderStatus.RENDERED
                    : RenderStatus.NOT_RENDERED;
            case DOCUMENT -> renderedDocumentIds.contains(node.document().getDocumentId())
                    ? RenderStatus.RENDERED
                    : RenderStatus.NOT_RENDERED;
            case BOX -> isBoxRendered(treeItem) ? RenderStatus.RENDERED : RenderStatus.NOT_RENDERED;
        };
    }

    private boolean isBoxRendered(TreeItem<SidebarNode> boxItem) {
        if (boxItem == null || boxItem.getChildren().isEmpty()) {
            return false;
        }
        for (TreeItem<SidebarNode> docItem : boxItem.getChildren()) {
            SidebarNode value = docItem.getValue();
            if (value == null || value.kind() != SidebarNode.Kind.DOCUMENT) {
                continue;
            }
            if (!renderedDocumentIds.contains(value.document().getDocumentId())) {
                return false;
            }
        }
        return true;
    }

    private void applyBadge(Label badgeLabel, RenderStatus status) {
        if (badgeLabel == null || status == null) {
            return;
        }
        badgeLabel.setText(status.label);
        badgeLabel.getStyleClass().setAll("status-badge", status.styleClass);
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
            applyDialogTheme(dialogPane);
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
    private final Set<Integer> renderedDocumentIds = new HashSet<>();

    @FXML
    private void onSaveCommand() {
        openExportDialog();
    }

    @FXML
    private void onExportMenuCommand() {
        openExportDialog();
    }

    private void openExportDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainController.class.getResource("/dk/easv/swiftdoc/view/export-dialog.fxml"));
            DialogPane dialogPane = loader.load();
            applyDialogTheme(dialogPane);
            ExportDialogController dialogController = loader.getController();

            Dialog<?> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Export");
            dialog.showAndWait();

            ExportDialogController.ExportRequest request = dialogController.getExportRequest();
            if (request == null) {
                return;
            }

            Integer boxId = resolveExportBoxId(request.mode());
            if (boxId == null) {
                showExportScopeError(request.mode());
                return;
            }

            lastResultLabel.setText("Exporting box #" + boxId + "...");
            Thread worker = new Thread(() -> performExport(boxId, request.outputDir()), "export-worker");
            worker.setDaemon(true);
            worker.start();
        } catch (IOException ex) {
            System.err.println("Failed to load export dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Integer resolveExportBoxId(ExportDialogController.ExportMode mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case ACTIVE_SESSION -> activeSession != null ? activeSession.getBox().getBoxId() : null;
            case SELECTED_SIDEBAR -> resolveBoxIdFromSelection(
                    sidebarTree.getSelectionModel().getSelectedItem());
        };
    }

    private Integer resolveBoxIdFromSelection(TreeItem<SidebarNode> selectedItem) {
        if (selectedItem == null || selectedItem.getValue() == null) {
            return null;
        }
        SidebarNode node = selectedItem.getValue();
        return switch (node.kind()) {
            case BOX -> node.box().getBoxId();
            case DOCUMENT -> node.document().getBoxId();
            case FILE -> {
                TreeItem<SidebarNode> docItem = selectedItem.getParent();
                if (docItem != null && docItem.getValue() != null
                        && docItem.getValue().kind() == SidebarNode.Kind.DOCUMENT) {
                    yield docItem.getValue().document().getBoxId();
                }
                TreeItem<SidebarNode> boxItem = docItem != null ? docItem.getParent() : null;
                if (boxItem != null && boxItem.getValue() != null
                        && boxItem.getValue().kind() == SidebarNode.Kind.BOX) {
                    yield boxItem.getValue().box().getBoxId();
                }
                yield null;
            }
        };
    }

    private void showExportScopeError(ExportDialogController.ExportMode mode) {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Nothing to export");
        if (mode == ExportDialogController.ExportMode.ACTIVE_SESSION) {
            info.setHeaderText("No active session");
            info.setContentText("Start a scan session first, or choose the selected sidebar item.");
        } else {
            info.setHeaderText("No sidebar selection");
            info.setContentText("Select a box, document, or file in the sidebar to export its box.");
        }
        info.showAndWait();
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
        if (result != null && result.exportedDocumentIds() != null) {
            renderedDocumentIds.addAll(result.exportedDocumentIds());
            sidebarTree.refresh();
        }

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

    @FXML
    private void onThemeToggle() {
        boolean darkMode = themeToggle != null && themeToggle.isSelected();
        applyTheme(darkMode);
    }

    private void applyTheme(boolean darkMode) {
        if (root == null) {
            return;
        }
        if (darkMode) {
            if (!root.getStyleClass().contains("theme-dark")) {
                root.getStyleClass().add("theme-dark");
            }
            if (themeToggle != null) {
                themeToggle.setText("Light");
            }
        } else {
            root.getStyleClass().remove("theme-dark");
            if (themeToggle != null) {
                themeToggle.setText("Dark");
            }
        }
    }

    private void applyDialogTheme(DialogPane dialogPane) {
        if (dialogPane == null) {
            return;
        }
        String sheet = MainController.class.getResource(
                "/dk/easv/swiftdoc/view/app.css").toExternalForm();
        if (!dialogPane.getStylesheets().contains(sheet)) {
            dialogPane.getStylesheets().add(sheet);
        }
        if (!dialogPane.getStyleClass().contains("dialog-root")) {
            dialogPane.getStyleClass().add("dialog-root");
        }
        if (root != null && root.getStyleClass().contains("theme-dark")
                && !dialogPane.getStyleClass().contains("theme-dark")) {
            dialogPane.getStyleClass().add("theme-dark");
        }
    }
}
