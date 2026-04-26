package dk.easv.swiftdoc.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class NewScanDialogController {
    @FXML
    private ComboBox<?> profileComboBox;

    @FXML
    private Label profileDescriptionLabel;

    @FXML
    private TextField boxNameTextField;

    @FXML
    private Label boxNameErrorLabel;

    @FXML
    private CheckBox autoSplitCheckBox;

    @FXML
    private CheckBox duplicateDetectionCheckBox;

    @FXML
    private CheckBox separateDocumentsCheckBox;

    @FXML
    private TextArea notesTextArea;

    @FXML
    private Button startScanButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button previewButton;

    @FXML
    private DialogPane dialogPane;

    @FXML
    private ButtonType startScanButtonType;

    @FXML
    private ButtonType cancelButtonType;

    @FXML
    private ButtonType previewButtonType;

    @FXML
    private void initialize() {
        startScanButton = (Button) dialogPane.lookupButton(startScanButtonType);
        cancelButton = (Button) dialogPane.lookupButton(cancelButtonType);
        previewButton = (Button) dialogPane.lookupButton(previewButtonType);

        startScanButton.setOnAction(event -> onStartScan());
        cancelButton.setOnAction(event -> onCancel());
        previewButton.setOnAction(event -> onPreview());

        // TODO: Load scanning profiles from database into profileComboBox
        // TODO: Set up profile selection listener to update profileDescriptionLabel
        // TODO: Add input validation listeners to boxNameTextField
    }

    @FXML
    private void onStartScan() {
        // TODO: Validate inputs (profile selected, box name non-empty)
        // TODO: Create scan session and pass configuration to service
        // TODO: Close dialog and return scan configuration result
    }

    @FXML
    private void onCancel() {
        // TODO: Close dialog without starting scan
    }

    @FXML
    private void onPreview() {
        // TODO: Show preview/summary dialog with all configured settings
    }
}
