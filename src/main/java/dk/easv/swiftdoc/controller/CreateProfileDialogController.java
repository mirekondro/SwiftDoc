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
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Controller for the "Create Profile" dialog.
 *
 * Replaces the inline Java-built VBox dialog that used to live inside
 * NewScanDialogController. Same three fields (name, client, duplicate
 * detection) but as proper FXML so it inherits the app's styling and
 * dark theme.
 *
 * Caller flow:
 *  1. Load create-profile-dialog.fxml
 *  2. Get the controller, call setClients(...)
 *  3. dialog.showAndWait()
 *  4. Read getCreateRequest() — null means cancelled, otherwise contains
 *     the new profile's fields
 */
public class CreateProfileDialogController {

    /**
     * Result returned by the dialog. All fields are validated by the
     * controller before this is constructed.
     */
    public record CreateRequest(String profileName, Client client,
                                boolean duplicateDetectionEnabled) {}

    private CreateRequest createRequest;

    @FXML private DialogPane dialogPane;
    @FXML private TextField profileNameField;
    @FXML private Label profileNameErrorLabel;
    @FXML private ComboBox<Client> clientComboBox;
    @FXML private Label clientErrorLabel;
    @FXML private CheckBox duplicateDetectionCheckBox;

    @FXML private ButtonType createButtonType;
    @FXML private ButtonType cancelButtonType;

    @FXML
    private void initialize() {
        // Validation listeners — clear errors and re-evaluate the Create button.
        profileNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateNameError();
            refreshCreateEnabled();
        });
        clientComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    updateClientError();
                    refreshCreateEnabled();
                });

        // Wire the Create button through an event filter so we can populate
        // createRequest before the dialog closes.
        Button createBtn = (Button) dialogPane.lookupButton(createButtonType);
        createBtn.addEventFilter(ActionEvent.ACTION, this::onCreate);

        updateNameError();
        updateClientError();
        refreshCreateEnabled();
    }

    /**
     * Populate the client dropdown. Called by the parent dialog before
     * showAndWait().
     */
    public void setClients(List<Client> clients) {
        clientComboBox.setItems(FXCollections.observableArrayList(clients));
        if (clients.isEmpty()) {
            clientErrorLabel.setText(
                    "No clients available. Ask an admin to create one first.");
        }
    }

    private void onCreate(ActionEvent event) {
        // Reset before validation — if anything fails we want the caller
        // to see null (i.e. "the user didn't successfully create").
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

        createRequest = new CreateRequest(
                name.trim(),
                client,
                duplicateDetectionCheckBox.isSelected());
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
            // Don't overwrite the "no clients available" message if it's set.
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

    /**
     * @return the validated fields if the user clicked Create successfully,
     *         or null if cancelled or validation failed.
     */
    public CreateRequest getCreateRequest() {
        return createRequest;
    }
}
