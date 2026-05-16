package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.model.User;
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
    private User currentUser;

    @FXML private DialogPane dialogPane;
    @FXML private ComboBox<ScanningProfile> profileComboBox;
    @FXML private Label profileDescriptionLabel;
    @FXML private TextField boxNameTextField;
    @FXML private Label boxNameErrorLabel;
    @FXML private CheckBox duplicateDetectionCheckBox;
    @FXML private TextArea notesTextArea;

    @FXML private ButtonType startScanButtonType;
    @FXML private ButtonType cancelButtonType;
    @FXML private ButtonType previewButtonType;

    @FXML
    private void initialize() {
        wireProfileSelection();
        wireValidation();
        wireButtons();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadProfiles();
    }

    private void loadProfiles() {
        try {
            List<ScanningProfile> profiles;
            if (currentUser != null && !currentUser.isAdmin()) {
                profiles = sessionService.getAvailableProfiles(currentUser.getUserId(), false);
            } else {
                profiles = sessionService.getAvailableProfiles();
            }
            profileComboBox.setItems(FXCollections.observableArrayList(profiles));
            if (profiles.isEmpty()) {
                profileDescriptionLabel.setText("No profiles available. Ask an admin to assign profiles to your account.");
            }
        } catch (SQLException ex) {
            showError("Could not load profiles", ex.getMessage());
            profileComboBox.setDisable(true);
        }
    }

    @FXML
    private void onCreateProfile() {
        try {
            // Load the dialog FXML.
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    NewScanDialogController.class.getResource(
                            "/dk/easv/swiftdoc/view/create-profile-dialog.fxml"));
            javafx.scene.control.DialogPane pane = loader.load();
            CreateProfileDialogController dialogController = loader.getController();

            // Populate the client dropdown.
            java.util.List<dk.easv.swiftdoc.model.Client> clients =
                    profileService.getClients();
            if (clients.isEmpty()) {
                showError("No clients", "Create a client first before adding profiles.");
                return;
            }
            dialogController.setClients(clients);

            // Apply the same theme/styling the parent dialog uses.
            applyDialogTheme(pane);

            javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog =
                    new javafx.scene.control.Dialog<>();
            dialog.setDialogPane(pane);
            dialog.setTitle("Create Profile");
            dialog.showAndWait();

            CreateProfileDialogController.CreateRequest request =
                    dialogController.getCreateRequest();
            if (request == null) {
                return; // user cancelled or validation failed
            }

            try {
                dk.easv.swiftdoc.model.ScanningProfile created =
                        profileService.createProfile(
                                request.profileName(),
                                request.client(),
                                request.duplicateDetectionEnabled());
                loadProfiles();
                profileComboBox.getSelectionModel().select(created);
            } catch (java.sql.SQLException | IllegalArgumentException ex) {
                showError("Could not create profile", ex.getMessage());
            }
        } catch (java.sql.SQLException ex) {
            showError("Could not load clients", ex.getMessage());
        } catch (java.io.IOException ex) {
            showError("Could not open dialog", ex.getMessage());
        }
    }

    private void wireProfileSelection() {
        profileComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) {
                        profileDescriptionLabel.setText("Profile details will appear here");
                        duplicateDetectionCheckBox.setSelected(false);
                    } else {
                        StringBuilder description = new StringBuilder();
                        description.append("Client: ")
                                .append(newVal.getClientName() == null ? "(unknown)" : newVal.getClientName());
                        String rule = newVal.getSplitRule();
                        if (rule != null && !rule.isBlank()) {
                            description.append("\nRule: ").append(rule);
                        }
                        description.append("\nDuplicate detection: ")
                                .append(newVal.isDuplicateDetectionEnabled() ? "Enabled" : "Disabled");
                        profileDescriptionLabel.setText(description.toString());
                        duplicateDetectionCheckBox.setSelected(newVal.isDuplicateDetectionEnabled());
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

        duplicateDetectionCheckBox.setDisable(true);
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
        summary.append("  Duplicate detection:   ")
                .append(profile != null && profile.isDuplicateDetectionEnabled()).append("\n");
        summary.append("  Separate document per page: ");

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

    private void applyDialogTheme(javafx.scene.control.DialogPane pane) {
        if (pane == null) return;
        String sheet = NewScanDialogController.class.getResource(
                "/dk/easv/swiftdoc/view/app.css").toExternalForm();
        if (!pane.getStylesheets().contains(sheet)) {
            pane.getStylesheets().add(sheet);
        }
        if (!pane.getStyleClass().contains("dialog-root")) {
            pane.getStyleClass().add("dialog-root");
        }
        // Mirror parent dialog's dark-mode class if present.
        if (dialogPane != null && dialogPane.getStyleClass().contains("theme-dark")
                && !pane.getStyleClass().contains("theme-dark")) {
            pane.getStyleClass().add("theme-dark");
        }
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
