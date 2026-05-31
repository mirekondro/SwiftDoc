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

    @FXML private Button btnProfiles;
    @FXML private Button btnClients;
    @FXML private Button btnUsers;
    @FXML private Button btnLogs;

    private User currentUser;
    private Runnable onLogout;

    @FXML
    private void onSelectProfiles() { 
        adminTabPane.getSelectionModel().select(0);
        updateActiveButton(btnProfiles);
    }
    @FXML
    private void onSelectClients() { 
        adminTabPane.getSelectionModel().select(1);
        updateActiveButton(btnClients);
    }
    @FXML
    private void onSelectUsers() { 
        adminTabPane.getSelectionModel().select(2);
        updateActiveButton(btnUsers);
    }
    @FXML
    private void onSelectLogs() { 
        adminTabPane.getSelectionModel().select(3);
        updateActiveButton(btnLogs);
    }

    private void updateActiveButton(Button activeBtn) {
        btnProfiles.getStyleClass().remove("btn-sidebar-active");
        btnClients.getStyleClass().remove("btn-sidebar-active");
        btnUsers.getStyleClass().remove("btn-sidebar-active");
        btnLogs.getStyleClass().remove("btn-sidebar-active");
        activeBtn.getStyleClass().add("btn-sidebar-active");
    }

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
        dialog.setTitle("Add Client");
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

    @FXML
    private void onRenameClient() {
        if (clientsList == null) return;
        Client selected = clientsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No client selected", "Select a client to rename.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.getClientName());
        dialog.setTitle("Rename Client");
        dialog.setHeaderText("Rename \"" + selected.getClientName() + "\"");
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(newName -> {
            try {
                profileService.renameClient(selected, newName);
                onRefreshClients();
            } catch (IllegalArgumentException | SQLException ex) {
                showError("Could not rename client", ex.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteClient() {
        if (clientsList == null) return;
        Client selected = clientsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No client selected", "Select a client to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Client");
        confirm.setHeaderText("Delete \"" + selected.getClientName() + "\"?");
        confirm.setContentText("This action cannot be undone. Clients with attached profiles cannot be deleted.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                profileService.deleteClient(selected);
                onRefreshClients();
            } catch (IllegalStateException | IllegalArgumentException | SQLException ex) {
                showError("Could not delete client", ex.getMessage());
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

    private void showInfo(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}