package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.service.ScanSessionService.ScanSession;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.io.IOException;

public class MainController {

    /**
     * The currently active scanning session.
     * Set when the user successfully starts a scan; null when no session is active.
     * US-09 picks this up to know which document to attach incoming files to.
     */
    private ScanSession activeSession;

    @FXML
    private void initialize() {
        // TODO: initialize the main view
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
                System.out.println("Scan session started — Box id: " + started.box().getBoxId()
                        + " (" + started.box().getBoxName() + "), "
                        + "first Document id: " + started.firstDocument().getDocumentId());
                // TODO: enable scanning UI, show box info in main view, etc. (rest of US-09)
            } else {
                System.out.println("New Scan cancelled — no session started.");
            }
        } catch (IOException ex) {
            System.err.println("Failed to load new scan dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    private void onSaveCommand() {
        // TODO: handle save command
    }

    public ScanSession getActiveSession() {
        return activeSession;
    }
}
