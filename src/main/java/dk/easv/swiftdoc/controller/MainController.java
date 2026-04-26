package dk.easv.swiftdoc.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.io.IOException;

public class MainController {
    @FXML
    private void initialize() {
        // TODO: initialize the main view
    }

    @FXML
    private void onNewCommand() {
        try {
            FXMLLoader loader = new FXMLLoader(MainController.class.getResource("/dk/easv/swiftdoc/view/new-scan-dialog.fxml"));
            DialogPane dialogPane = loader.load();
            Dialog<?> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("New Scan");
            dialog.showAndWait();
        } catch (IOException ex) {
            System.err.println("Failed to load new scan dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    private void onSaveCommand() {
        // TODO: handle save command
    }
}

