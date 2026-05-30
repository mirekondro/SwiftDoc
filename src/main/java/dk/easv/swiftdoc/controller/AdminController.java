package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.service.ProfileService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;

public class AdminController {

    private final ProfileService profileService = new ProfileService();

    @FXML private Label welcomeLabel;
    @FXML private ListView<Client> clientsList;
    @FXML private ImageView brandLogo;
    @FXML private TabPane adminTabPane;
    @FXML private UserManagementController userManagementController;

    private User currentUser;
    private Runnable onLogout;

    @FXML
    private void onSelectProfiles() { adminTabPane.getSelectionModel().select(0); }
    @FXML
    private void onSelectClients() { adminTabPane.getSelectionModel().select(1); }
    @FXML
    private void onSelectUsers() { adminTabPane.getSelectionModel().select(2); }
    @FXML
    private void onSelectLogs() { adminTabPane.getSelectionModel().select(3); }

    @FXML
    private void initialize() {
        // Profiles are handled by the included ProfileManagementController.
        // Only load clients here if this view still owns a clients list.
        javafx.application.Platform.runLater(() -> {
            if (clientsList != null) {
                onRefreshClients();
            }
        });
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (welcomeLabel != null && user != null) {
            welcomeLabel.setText("Signed in as " + user.getUsername() + " (" + user.getRole() + ")");
        }
        if (userManagementController != null) {
            userManagementController.setCurrentAdmin(user);
        }
    }

    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    // ---------------- Clients ----------------

    @FXML
    private void onRefreshClients() {
        if (clientsList == null) return;
        try {
            List<Client> clients = profileService.getClients();
            clientsList.setItems(FXCollections.observableArrayList(clients));
        } catch (SQLException ex) {
            showError("Could not load clients", ex.getMessage());
        }
    }

    @FXML
    private void onCreateClient() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Client");
        dialog.setHeaderText("Add a new client");
        dialog.setContentText("Client name:");

        dialog.showAndWait().ifPresent(name -> {
            try {
                profileService.createClient(name);
                onRefreshClients();
            } catch (IllegalArgumentException | SQLException ex) {
                showError("Could not create client", ex.getMessage());
            }
        });
    }

    // ---------------- Session ----------------

    @FXML
    private void onLogoutCommand() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.close();
        if (onLogout != null) {
            onLogout.run();
        }
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}