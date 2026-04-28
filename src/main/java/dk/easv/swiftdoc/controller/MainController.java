package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.service.ScanService;
import dk.easv.swiftdoc.service.ScanService.ScanResult;
import dk.easv.swiftdoc.service.ScanSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;

import java.io.IOException;
import java.util.List;

public class MainController {

    private final ScanService scanService = new ScanService();

    /**
     * The currently active scanning session.
     * Set after the user successfully starts a scan; null when no session
     * is active.
     */
    private ScanSession activeSession;

    @FXML private Button scanButton;
    @FXML private Label sessionInfoLabel;
    @FXML private Label counterLabel;
    @FXML private Label lastResultLabel;

    @FXML
    private void initialize() {
        // No active session at startup — Scan button starts disabled (set in FXML),
        // labels show placeholder text. Nothing else needed here yet.
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

    /** Called once a session is created — enables Scan, updates info labels. */
    private void onSessionStarted() {
        scanButton.setDisable(false);
        refreshSessionLabels();
        lastResultLabel.setText("Ready. Press Scan to fetch the next page.");
        System.out.println("Session started — Box id "
                + activeSession.getBox().getBoxId()
                + ", first Document id " + activeSession.getFirstDocument().getDocumentId());
    }

    /**
     * Scan button handler. Runs the actual scan on a background thread so
     * the UI stays responsive — the API call can take a couple of seconds.
     * UI updates always happen back on the JavaFX thread via Platform.runLater.
     */
    @FXML
    private void onScanCommand() {
        if (activeSession == null) {
            // Defensive: button shouldn't be enabled without a session, but
            // belt-and-suspenders.
            return;
        }

        scanButton.setDisable(true);
        lastResultLabel.setText("Scanning...");

        Thread worker = new Thread(this::performScan, "scan-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Runs on background thread. Must NOT touch JavaFX nodes directly. */
    private void performScan() {
        try {
            List<ScanResult> results = scanService.scan(activeSession);
            Platform.runLater(() -> applyResultsToUi(results));
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> showScanError(ex));
        }
    }

    /** Runs on JavaFX thread. */
    private void applyResultsToUi(List<ScanResult> results) {
        for (ScanResult r : results) {
            switch (r.kind()) {
                case PAGE -> {
                    lastResultLabel.setText("Page saved as File #"
                            + r.savedFile().getReferenceId()
                            + " (" + r.savedFile().getTiffData().length + " bytes)");
                    System.out.println("PAGE saved: File #" + r.savedFile().getReferenceId()
                            + " in Document " + r.savedFile().getDocumentId());
                }
                case DOCUMENT_SPLIT -> {
                    lastResultLabel.setText("Barcode \"" + r.barcodeValue()
                            + "\" detected — new Document #"
                            + r.newDocument().getDocumentNumber() + " started");
                    System.out.println("SPLIT: barcode " + r.barcodeValue()
                            + " → Document id " + r.newDocument().getDocumentId());
                }
            }
        }

        refreshSessionLabels();
        scanButton.setDisable(false);
    }

    private void refreshSessionLabels() {
        sessionInfoLabel.setText(
                "Box #" + activeSession.getBox().getBoxId() + " "
                        + "(" + activeSession.getBox().getBoxName() + ") — "
                        + "current document: #"
                        + activeSession.getCurrentDocument().getDocumentNumber());
        counterLabel.setText("Files scanned: " + activeSession.getTotalFileCount());
    }

    private void showScanError(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Scan failed");
        alert.setHeaderText("Could not complete the scan");
        alert.setContentText(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        alert.showAndWait();

        lastResultLabel.setText("Last scan failed: " + ex.getMessage());
        scanButton.setDisable(false);
    }

    @FXML
    private void onSaveCommand() {
        // TODO: handle save command
    }

    public ScanSession getActiveSession() {
        return activeSession;
    }
}
