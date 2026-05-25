package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.service.ProfileService;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProfileManagementController {

    private final ProfileService profileService = new ProfileService();

    @FXML private TableView<ScanningProfile>            profilesTable;
    @FXML private TableColumn<ScanningProfile, String>  colName;
    @FXML private TableColumn<ScanningProfile, String>  colClient;
    @FXML private TableColumn<ScanningProfile, Boolean> colDupDetect;

    @FXML private Label    formTitleLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<Client> clientCombo;
    @FXML private CheckBox dupDetectCheck;
    @FXML private Label    messageLabel;

    private ScanningProfile editingProfile;
    private List<Client> clients;

    @FXML
    private void initialize() {
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProfileName()));
        colClient.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getClientName()));
        colDupDetect.setCellValueFactory(c ->
                new SimpleBooleanProperty(c.getValue().isDuplicateDetectionEnabled()).asObject());

        profilesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> onSelectionChanged(sel));

        clearForm();
        Platform.runLater(this::refresh);
    }

    private void refresh() {
        try {
            clients = profileService.getClients();
            clientCombo.setItems(FXCollections.observableArrayList(clients));

            List<ScanningProfile> profiles = profileService.getProfiles();
            profilesTable.setItems(FXCollections.observableArrayList(profiles));
        } catch (SQLException ex) {
            showError("Could not load profiles", ex.getMessage());
        }
    }

    private void onSelectionChanged(ScanningProfile profile) {
        if (profile == null) {
            clearForm();
            return;
        }
        editingProfile = null;
        formTitleLabel.setText("Profile: " + profile.getProfileName());
        nameField.setText(profile.getProfileName());
        dupDetectCheck.setSelected(profile.isDuplicateDetectionEnabled());
        messageLabel.setText("");

        if (clients != null) {
            clients.stream()
                    .filter(c -> c.getClientId() == profile.getClientId())
                    .findFirst()
                    .ifPresent(clientCombo.getSelectionModel()::select);
        }
    }

    @FXML
    private void onCreateClicked() {
        profilesTable.getSelectionModel().clearSelection();
        editingProfile = null;
        formTitleLabel.setText("New profile");
        nameField.clear();
        dupDetectCheck.setSelected(false);
        if (clients != null && !clients.isEmpty()) {
            clientCombo.getSelectionModel().selectFirst();
        }
        messageLabel.setText("");
        nameField.requestFocus();
    }

    @FXML
    private void onEditClicked() {
        ScanningProfile selected = profilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a profile to edit.", true); return; }
        editingProfile = selected;
        formTitleLabel.setText("Edit: " + selected.getProfileName());
        nameField.setText(selected.getProfileName());
        dupDetectCheck.setSelected(selected.isDuplicateDetectionEnabled());
        messageLabel.setText("");
        if (clients != null) {
            clients.stream()
                    .filter(c -> c.getClientId() == selected.getClientId())
                    .findFirst()
                    .ifPresent(clientCombo.getSelectionModel()::select);
        }
        nameField.requestFocus();
    }

    @FXML
    private void onDeleteClicked() {
        ScanningProfile selected = profilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a profile to delete.", true); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Profile");
        confirm.setHeaderText("Delete '" + selected.getProfileName() + "'?");
        confirm.setContentText("This cannot be undone. Profiles with existing boxes cannot be deleted.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            profileService.deleteProfile(selected.getProfileId());
            clearForm();
            refresh();
            showMessage("Profile deleted.", false);
        } catch (IllegalStateException ex) {
            showMessage(ex.getMessage(), true);
        } catch (SQLException ex) {
            showError("Delete failed", ex.getMessage());
        }
    }

    @FXML
    private void onSaveClicked() {
        String name = nameField.getText();
        Client client = clientCombo.getValue();

        if (name == null || name.isBlank()) { showMessage("Profile name is required.", true); return; }
        if (client == null)                 { showMessage("Client is required.", true); return; }

        try {
            if (editingProfile == null) {
                profileService.createProfile(name, client, dupDetectCheck.isSelected());
                showMessage("Profile created.", false);
            } else {
                profileService.updateProfile(editingProfile.getProfileId(), name, client,
                        dupDetectCheck.isSelected());
                showMessage("Profile saved.", false);
            }
            editingProfile = null;
            refresh();
            clearForm();
        } catch (IllegalArgumentException | SQLException ex) {
            showMessage(ex.getMessage(), true);
        }
    }

    @FXML
    private void onCancelClicked() {
        clearForm();
    }

    private void clearForm() {
        editingProfile = null;
        formTitleLabel.setText("Select a profile or click Create");
        nameField.clear();
        dupDetectCheck.setSelected(false);
        clientCombo.getSelectionModel().clearSelection();
        messageLabel.setText("");
        profilesTable.getSelectionModel().clearSelection();
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.setStyle(error ? "-fx-text-fill: #cc0000;" : "-fx-text-fill: #007700;");
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
