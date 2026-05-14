package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.model.User.Role;
import dk.easv.swiftdoc.service.AuthService;
import dk.easv.swiftdoc.service.ProfileService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class AdminController {

    private final ProfileService profileService = new ProfileService();
    private final AuthService authService = new AuthService();

    @FXML private Label welcomeLabel;
    @FXML private ListView<ScanningProfile> profilesList;
    @FXML private Label profileDetailsLabel;
    @FXML private ListView<Client> clientsList;
    @FXML private ListView<User> usersList;
    @FXML private TextField newUsernameField;
    @FXML private PasswordField newPasswordField;
    @FXML private ComboBox<Role> newRoleCombo;
    @FXML private Label userMessageLabel;

    private User currentUser;

    @FXML
    private void initialize() {
        newRoleCombo.setItems(FXCollections.observableArrayList(Role.USER, Role.ADMIN));
        newRoleCombo.getSelectionModel().select(Role.USER);

        profilesList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> showProfileDetails(newVal));

        Platform.runLater(this::refreshAll);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (welcomeLabel != null && user != null) {
            welcomeLabel.setText("Signed in as " + user.getUsername() + " (" + user.getRole() + ")");
        }
    }

    // ---------------- Profiles ----------------

    @FXML
    private void onRefreshProfiles() {
        try {
            List<ScanningProfile> profiles = profileService.getProfiles();
            profilesList.setItems(FXCollections.observableArrayList(profiles));
        } catch (SQLException ex) {
            showError("Could not load profiles", ex.getMessage());
        }
    }

    @FXML
    private void onCreateProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AdminController.class.getResource("/dk/easv/swiftdoc/view/create-profile-dialog.fxml"));
            DialogPane pane = loader.load();
            CreateProfileDialogController dialogController = loader.getController();

            List<Client> clients = profileService.getClients();
            if (clients.isEmpty()) {
                showError("No clients", "Add a client to dbo.Clients before creating a profile.");
                return;
            }
            dialogController.setClients(clients);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(pane);
            dialog.setTitle("Create Profile");
            dialog.showAndWait();

            CreateProfileDialogController.CreateRequest req = dialogController.getCreateRequest();
            if (req == null) {
                return;
            }
            profileService.createProfile(req.profileName(), req.client(), req.duplicateDetectionEnabled());
            onRefreshProfiles();
        } catch (IOException | SQLException | IllegalArgumentException ex) {
            showError("Could not create profile", ex.getMessage());
        }
    }

    private void showProfileDetails(ScanningProfile profile) {
        if (profile == null) {
            profileDetailsLabel.setText("Select a profile to see details");
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append("Name: ").append(profile.getProfileName()).append('\n');
        b.append("Client: ").append(profile.getClientName()).append('\n');
        b.append("Split rule: ").append(profile.getSplitRule() == null ? "(none)" : profile.getSplitRule()).append('\n');
        b.append("Duplicate detection: ")
                .append(profile.isDuplicateDetectionEnabled() ? "Enabled" : "Disabled");
        profileDetailsLabel.setText(b.toString());
    }

    // ---------------- Clients ----------------

    @FXML
    private void onRefreshClients() {
        try {
            List<Client> clients = profileService.getClients();
            clientsList.setItems(FXCollections.observableArrayList(clients));
        } catch (SQLException ex) {
            showError("Could not load clients", ex.getMessage());
        }
    }

    // ---------------- Users ----------------

    @FXML
    private void onRefreshUsers() {
        try {
            List<User> users = authService.listUsers();
            usersList.setItems(FXCollections.observableArrayList(users));
        } catch (SQLException ex) {
            showError("Could not load users", ex.getMessage());
        }
    }

    @FXML
    private void onCreateUser() {
        userMessageLabel.setText("");
        String username = newUsernameField.getText();
        String password = newPasswordField.getText();
        Role role = newRoleCombo.getSelectionModel().getSelectedItem();
        if (role == null) role = Role.USER;
        try {
            authService.createUser(username, password, role);
            newUsernameField.clear();
            newPasswordField.clear();
            newRoleCombo.getSelectionModel().select(Role.USER);
            userMessageLabel.setStyle("-fx-text-fill: #2a7a2a;");
            userMessageLabel.setText("User '" + username + "' created.");
            onRefreshUsers();
        } catch (IllegalArgumentException | SQLException ex) {
            userMessageLabel.setStyle("-fx-text-fill: #cc0000;");
            userMessageLabel.setText("Could not create user: " + ex.getMessage());
        }
    }

    @FXML
    private void onDeleteUser() {
        User selected = usersList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (currentUser != null && selected.getUserId() == currentUser.getUserId()) {
            showError("Cannot delete", "You cannot delete the account you are signed in with.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete user '" + selected.getUsername() + "'?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Confirm delete");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    authService.deleteUser(selected.getUserId());
                    onRefreshUsers();
                } catch (SQLException ex) {
                    showError("Could not delete user", ex.getMessage());
                }
            }
        });
    }

    // ---------------- Session ----------------

    @FXML
    private void onSignOut() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.close();
        Platform.exit();
    }

    private void refreshAll() {
        onRefreshProfiles();
        onRefreshClients();
        onRefreshUsers();
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
