package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

import java.util.List;


public class CreateProfileDialogController {

    /**
     * Result returned by the dialog. All fields validated before construction.
     */
    public record CreateRequest(String profileName, Client client,
                                boolean duplicateDetectionEnabled,
                                int profileRotation, int profileBrightness,
                                boolean blackAndWhite) {}

    private CreateRequest createRequest;

    @FXML private DialogPane dialogPane;
    @FXML private TextField profileNameField;
    @FXML private Label profileNameErrorLabel;
    @FXML private ComboBox<Client> clientComboBox;
    @FXML private Label clientErrorLabel;
    @FXML private CheckBox duplicateDetectionCheckBox;

    // Processing controls
    @FXML private Spinner<Integer> rotationSpinner;
    @FXML private Slider brightnessSlider;
    @FXML private Label brightnessValueLabel;
    @FXML private CheckBox blackAndWhiteCheckBox;

    @FXML private ButtonType createButtonType;
    @FXML private ButtonType cancelButtonType;

    @FXML
    private void initialize() {
        // Rotation spinner: accept -359..359 (negatives = counter-clockwise),
        // normalized when read.
        rotationSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(-359, 359, 0, 1));

        // Brightness slider → live label.
        brightnessValueLabel.setText("0");
        brightnessSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                brightnessValueLabel.setText(Integer.toString(newVal.intValue())));

        // Validation listeners.
        profileNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateNameError();
            refreshCreateEnabled();
        });
        clientComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    updateClientError();
                    refreshCreateEnabled();
                });

        Button createBtn = (Button) dialogPane.lookupButton(createButtonType);
        createBtn.addEventFilter(ActionEvent.ACTION, this::onCreate);

        updateNameError();
        updateClientError();
        refreshCreateEnabled();
    }

    public void setClients(List<Client> clients) {
        clientComboBox.setItems(FXCollections.observableArrayList(clients));
        if (clients.isEmpty()) {
            clientErrorLabel.setText(
                    "No clients available. Ask an admin to create one first.");
        }
    }

    private void onCreate(ActionEvent event) {
        createRequest = null;

        String name = profileNameField.getText();
        Client client = clientComboBox.getValue();

        boolean nameOk = name != null && !name.isBlank();
        boolean clientOk = client != null;

        if (!nameOk || !clientOk) {
            updateNameError();
            updateClientError();
            event.consume();
            return;
        }

        int rotation = normalizeRotation(
                rotationSpinner.getValue() != null ? rotationSpinner.getValue() : 0);
        int brightness = (int) Math.round(brightnessSlider.getValue());
        boolean bw = blackAndWhiteCheckBox.isSelected();

        createRequest = new CreateRequest(
                name.trim(), client,
                duplicateDetectionCheckBox.isSelected(),
                rotation, brightness, bw);
    }

    private void updateNameError() {
        String name = profileNameField.getText();
        if (name == null || name.isBlank()) {
            profileNameErrorLabel.setText("Profile name is required.");
        } else {
            profileNameErrorLabel.setText("");
        }
    }

    private void updateClientError() {
        if (clientComboBox.getValue() == null) {
            if (clientComboBox.getItems().isEmpty()) {
                return;
            }
            clientErrorLabel.setText("Choose a client.");
        } else {
            clientErrorLabel.setText("");
        }
    }

    private void refreshCreateEnabled() {
        Button createBtn = (Button) dialogPane.lookupButton(createButtonType);
        if (createBtn == null) return;
        boolean nameOk = profileNameField.getText() != null
                && !profileNameField.getText().isBlank();
        boolean clientOk = clientComboBox.getValue() != null;
        createBtn.setDisable(!(nameOk && clientOk));
    }

    private static int normalizeRotation(int degrees) {
        int mod = degrees % 360;
        return mod < 0 ? mod + 360 : mod;
    }

    public CreateRequest getCreateRequest() {
        return createRequest;
    }
}
