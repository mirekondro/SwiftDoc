package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.service.ProfileService;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory;


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
    @FXML private Spinner<Integer> rotationSpinner;
    @FXML private Slider   brightnessSlider;
    @FXML private Label    brightnessValueLabel;
    @FXML private CheckBox blackAndWhiteCheck;
    @FXML private Label    messageLabel;
    @FXML private Button disableButton;
    @FXML private TableColumn<ScanningProfile, String> colStatus;

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
        if (colStatus != null) {
            colStatus.setCellValueFactory(c ->
                    new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Disabled"));
            colStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    getStyleClass().removeAll("text-success", "text-error");
                    if (!empty) {
                        getStyleClass().add("Active".equals(item) ? "text-success" : "text-error");
                        setStyle("-fx-font-weight: 600;");
                    }
                }
            });
        }
        profilesTable.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(ScanningProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                setOpacity(empty || profile == null || profile.isActive() ? 1.0 : 0.55);
            }
        });

        profilesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> onSelectionChanged(sel));

        rotationSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 359, 0));
        brightnessSlider.valueProperty().addListener((obs, o, n) ->
                brightnessValueLabel.setText(String.valueOf(n.intValue())));

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
        loadProcessing(profile);
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
        resetProcessing();
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
        loadProcessing(selected);
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
    private void onDisableClicked() {
        ScanningProfile selected = profilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a profile first.", true); return; }
        showMessage("Disable/enable for profiles is not implemented yet.", true);
    }

    @FXML
    private void onRotationDecrement() {
        if (rotationSpinner.getValueFactory() != null) {
            rotationSpinner.getValueFactory().decrement(90);
        }
    }

    @FXML
    private void onRotationIncrement() {
        if (rotationSpinner.getValueFactory() != null) {
            rotationSpinner.getValueFactory().increment(90);
        }
    }

    @FXML
    private void onSaveClicked() {
        String name = nameField.getText();
        Client client = clientCombo.getValue();

        if (name == null || name.isBlank()) { showMessage("Profile name is required.", true); return; }
        if (client == null)                 { showMessage("Client is required.", true); return; }

        int rotation = rotationSpinner.getValue() == null ? 0 : rotationSpinner.getValue();
        int brightness = (int) Math.round(brightnessSlider.getValue());
        boolean blackAndWhite = blackAndWhiteCheck.isSelected();

        try {
            if (editingProfile == null) {
                profileService.createProfile(name, client, dupDetectCheck.isSelected(),
                        rotation, brightness, blackAndWhite);
                showMessage("Profile created.", false);
            } else {
                profileService.updateProfile(editingProfile.getProfileId(), name, client,
                        dupDetectCheck.isSelected(), rotation, brightness, blackAndWhite);
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
        resetProcessing();
        clientCombo.getSelectionModel().clearSelection();
        messageLabel.setText("");
        profilesTable.getSelectionModel().clearSelection();
    }

    private void loadProcessing(ScanningProfile p) {
        rotationSpinner.getValueFactory().setValue(p.getProfileRotation());
        brightnessSlider.setValue(p.getProfileBrightness());
        brightnessValueLabel.setText(String.valueOf(p.getProfileBrightness()));
        blackAndWhiteCheck.setSelected(p.isBlackAndWhite());
    }

    private void resetProcessing() {
        rotationSpinner.getValueFactory().setValue(0);
        brightnessSlider.setValue(0);
        brightnessValueLabel.setText("0");
        blackAndWhiteCheck.setSelected(false);
    }

    private void showMessage(String msg, boolean error) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("text-error", "text-success");
        messageLabel.getStyleClass().add(error ? "text-error" : "text-success");
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
