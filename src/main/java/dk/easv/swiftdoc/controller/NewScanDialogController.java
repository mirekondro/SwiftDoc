package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.service.ProfileService;
import dk.easv.swiftdoc.service.ScanSession;
import dk.easv.swiftdoc.service.ScanSessionService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;

/**
 * Controller for the New Scan dialog (US-08).
 *
 * On Start Scan, creates a Box + its first Document via {@link ScanSessionService}
 * and exposes both via {@link #getCreatedSession()}.
 *
 * MainController reads getCreatedSession() after the dialog closes; null
 * means the user cancelled or something failed.
 */
public class NewScanDialogController {

    private final ScanSessionService sessionService = new ScanSessionService();
    private final ProfileService profileService = new ProfileService();

    /** Result of the dialog. Null until Start Scan succeeds. */
    private ScanSession createdSession;

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

    @FXML
    private void onCreateProfile() {
        try {
            List<Client> clients = profileService.getClients();
            if (clients.isEmpty()) {
                showError("No clients", "Create a client first before adding profiles.");
                return;
            }

            DialogPane pane = new DialogPane();
            pane.setHeaderText("Create Profile");

            TextField nameField = new TextField();
            nameField.setPromptText("Profile name");

            ComboBox<Client> clientBox = new ComboBox<>(FXCollections.observableArrayList(clients));
            clientBox.setPromptText("Select client");

            VBox content = new VBox(8.0, new Label("Profile Name"), nameField,
                    new Label("Client"), clientBox);
            pane.setContent(content);

            ButtonType create = new ButtonType("Create", ButtonType.OK.getButtonData());
            pane.getButtonTypes().addAll(create, ButtonType.CANCEL);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(pane);
            dialog.setTitle("Create Profile");

            dialog.showAndWait().ifPresent(result -> {
                if (result == create) {
                    try {
                        ScanningProfile created = profileService.createProfile(
                                nameField.getText(), clientBox.getValue());
                        loadProfiles();
                        profileComboBox.getSelectionModel().select(created);
                    } catch (SQLException | IllegalArgumentException ex) {
                        showError("Could not create profile", ex.getMessage());
                    }
                }
            });
        } catch (SQLException ex) {
            showError("Could not load clients", ex.getMessage());
        }
    }

    private void wireProfileSelection() {
        profileComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) {
                        profileDescriptionLabel.setText("Profile details will appear here");
                    } else {
                        // ScanningProfile now has SplitRule (was: description)
                        String rule = newVal.getSplitRule();
                        profileDescriptionLabel.setText(
                                (rule == null || rule.isBlank())
                                        ? "(no split rule configured)"
                                        : rule);
                    }
                    refreshStartEnabled();
                });
    }

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

    private void wireButtons() {
        Button startBtn = (Button) dialogPane.lookupButton(startScanButtonType);
        Button previewBtn = (Button) dialogPane.lookupButton(previewButtonType);

        startBtn.addEventFilter(ActionEvent.ACTION, this::onStartScan);

        previewBtn.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            onPreview();
        });

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

    private void onStartScan(ActionEvent event) {
        ScanningProfile profile = profileComboBox.getValue();
        String boxName = boxNameTextField.getText();

        // Reset before attempting — if anything fails, getCreatedSession()
        // must return null so callers treat it as a cancel/abort.
        createdSession = null;

        try {
            createdSession = sessionService.startSession(profile, boxName);
        } catch (IllegalArgumentException ex) {
            event.consume();
            showError("Invalid input", ex.getMessage());
        } catch (SQLException ex) {
            event.consume();
            showError("Database error", "Could not create the scan session:\n" + ex.getMessage());
        }
    }

    private void onPreview() {
        ScanningProfile profile = profileComboBox.getValue();
        String boxName = boxNameTextField.getText();
        String notes = notesTextArea.getText();

        StringBuilder summary = new StringBuilder();
        summary.append("Profile: ")
                .append(profile == null ? "(none selected)" : profile.getProfileName())
                .append("\n");
        summary.append("Client: ")
                .append(profile == null ? "(none selected)" : profile.getClientName())
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
     * @return the ScanSession (Box + first Document) created by Start Scan,
     *         or null if the dialog was cancelled, closed, or hit an error.
     *         A non-null return guarantees both rows exist in the database.
     */
    public ScanSession getCreatedSession() {
        return createdSession;
    }
}
