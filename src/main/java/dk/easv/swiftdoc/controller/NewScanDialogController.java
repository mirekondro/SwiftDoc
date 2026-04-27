package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Box;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.service.ScanSessionService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.util.List;

/**
 * Controller for the New Scan dialog (US-08).
 *
 * Responsibilities:
 *  - Load available scanning profiles into the dropdown.
 *  - Live-validate that a profile is selected and a box name is entered.
 *  - On Start Scan, create the Box and expose it via {@link #getCreatedBox()}.
 *  - On Cancel, close the dialog without creating anything.
 *  - On Preview, show a summary alert without committing.
 *
 * The MainController reads {@link #getCreatedBox()} after the dialog closes
 * to know whether a session actually started.
 */
public class NewScanDialogController {

    private final ScanSessionService sessionService = new ScanSessionService();

    /** Result of the dialog. Null until Start Scan succeeds. */
    private Box createdBox;

    @FXML private DialogPane dialogPane;
    @FXML private ComboBox<ScanningProfile> profileComboBox;
    @FXML private Label profileDescriptionLabel;
    @FXML private TextField boxNameTextField;
    @FXML private Label boxNameErrorLabel;
    @FXML private CheckBox autoSplitCheckBox;
    @FXML private CheckBox duplicateDetectionCheckBox;
    @FXML private CheckBox separateDocumentsCheckBox;
    @FXML private TextArea notesTextArea;

    @FXML private ButtonType startScanButtonType;
    @FXML private ButtonType cancelButtonType;
    @FXML private ButtonType previewButtonType;

    @FXML
    private void initialize() {
        loadProfiles();
        wireProfileSelection();
        wireValidation();
        wireButtons();
    }

    /**
     * Load profiles from DB. If it fails, show error and disable Start.
     */
    private void loadProfiles() {
        try {
            List<ScanningProfile> profiles = sessionService.getAvailableProfiles();
            profileComboBox.setItems(FXCollections.observableArrayList(profiles));
            if (profiles.isEmpty()) {
                profileDescriptionLabel.setText("No profiles available. Ask an admin to create one.");
            }
        } catch (SQLException ex) {
            showError("Could not load profiles", ex.getMessage());
            profileComboBox.setDisable(true);
        }
    }

    /**
     * When a profile is picked, show its description in the side panel.
     */
    private void wireProfileSelection() {
        profileComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) {
                        profileDescriptionLabel.setText("Profile details will appear here");
                    } else {
                        String desc = newVal.getDescription();
                        profileDescriptionLabel.setText(
                                (desc == null || desc.isBlank())
                                        ? "(no description)"
                                        : desc);
                    }
                    refreshStartEnabled();
                });
    }

    /**
     * Live validation of box name + start-button enable state.
     */
    private void wireValidation() {
        boxNameTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                boxNameErrorLabel.setText("Box name is required");
            } else {
                boxNameErrorLabel.setText("");
            }
            refreshStartEnabled();
        });
    }

    /**
     * Hook the dialog button actions. Start Scan must consume its event
     * if validation fails — otherwise the dialog closes regardless.
     */
    private void wireButtons() {
        Button startBtn = (Button) dialogPane.lookupButton(startScanButtonType);
        Button previewBtn = (Button) dialogPane.lookupButton(previewButtonType);
        // Cancel is wired automatically by JavaFX (CANCEL_CLOSE button data closes dialog).

        // Use addEventFilter so we can consume() and prevent dialog close on validation failure.
        startBtn.addEventFilter(ActionEvent.ACTION, this::onStartScan);

        // Preview must NOT close the dialog. ButtonType OTHER closes by default,
        // so we consume the event after showing the alert.
        previewBtn.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            onPreview();
        });

        // Start out disabled until inputs are valid.
        refreshStartEnabled();
    }

    private void refreshStartEnabled() {
        Button startBtn = (Button) dialogPane.lookupButton(startScanButtonType);
        if (startBtn == null) return;
        boolean profileChosen = profileComboBox.getValue() != null;
        boolean nameEntered = boxNameTextField.getText() != null
                && !boxNameTextField.getText().isBlank();
        startBtn.setDisable(!(profileChosen && nameEntered));
    }

    /**
     * Start Scan: validate, call service, capture the created Box.
     * If anything fails, consume the event so the dialog stays open.
     */
    private void onStartScan(ActionEvent event) {
        ScanningProfile profile = profileComboBox.getValue();
        String boxName = boxNameTextField.getText();

        // Reset before attempting — if anything fails, getCreatedBox() must
        // return null so callers treat it as a cancel/abort.
        createdBox = null;

        try {
            createdBox = sessionService.startSession(profile, boxName);
            // Success — let the event continue, dialog will close.
        } catch (IllegalArgumentException ex) {
            event.consume();
            showError("Invalid input", ex.getMessage());
        } catch (SQLException ex) {
            event.consume();
            showError("Database error", "Could not create the scan session:\n" + ex.getMessage());
        }
    }

    /**
     * Preview: show a read-only summary of what will be created.
     */
    private void onPreview() {
        ScanningProfile profile = profileComboBox.getValue();
        String boxName = boxNameTextField.getText();
        String notes = notesTextArea.getText();

        StringBuilder summary = new StringBuilder();
        summary.append("Profile: ")
                .append(profile == null ? "(none selected)" : profile.getProfileName())
                .append("\n");
        summary.append("Box name: ")
                .append((boxName == null || boxName.isBlank()) ? "(empty)" : boxName.trim())
                .append("\n\n");
        summary.append("Options:\n");
        summary.append("  Auto-split by barcode: ").append(autoSplitCheckBox.isSelected()).append("\n");
        summary.append("  Duplicate detection:   ").append(duplicateDetectionCheckBox.isSelected()).append("\n");
        summary.append("  Separate document per page: ").append(separateDocumentsCheckBox.isSelected()).append("\n");
        if (notes != null && !notes.isBlank()) {
            summary.append("\nNotes:\n").append(notes.trim());
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Preview Settings");
        alert.setHeaderText("This is what will be created:");
        alert.setContentText(summary.toString());
        alert.showAndWait();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * @return the Box created by Start Scan, or {@code null} if:
     *         <ul>
     *           <li>the user clicked Cancel</li>
     *           <li>the user closed the dialog (X button or Esc)</li>
     *           <li>Start Scan was clicked but failed validation or DB insert</li>
     *         </ul>
     *         A non-null return guarantees a Box row exists in the database.
     */
    public Box getCreatedBox() {
        return createdBox;
    }
}
