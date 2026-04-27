package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Box;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.io.IOException;

public class MainController {

    /**
     * The currently active scanning session's box.
     * Set when the user successfully starts a scan; null when no session is active.
     * Sprint 1 doesn't yet do anything with this besides hold it — US-09 picks it up
     * to know which box to attach incoming files to.
     */
    private Box activeBox;

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

            // After the dialog closes, check whether a session was actually started.
            // getCreatedBox() returns null for: Cancel button, X close, Esc key,
            // validation failure, or DB error. Only a successful Start Scan
            // produces a non-null Box.
            Box created = dialogController.getCreatedBox();
            if (created != null) {
                this.activeBox = created;
                System.out.println("Scan session started — box id: " + created.getBoxId()
                        + " (" + created.getBoxName() + ")");
                // TODO: enable scanning UI, show box info in main view, etc. (US-09 onward)
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

    public Box getActiveBox() {
        return activeBox;
    }
}
