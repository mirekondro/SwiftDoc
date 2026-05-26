package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.Client;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.service.ProfileService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class AdminController {

    private final ProfileService profileService = new ProfileService();

    @FXML private Label welcomeLabel;
    @FXML private ListView<ScanningProfile> profilesList;
    @FXML private Label profileDetailsLabel;
    @FXML private ListView<Client> clientsList;
    @FXML private ImageView brandLogo;
    @FXML private UserManagementController userManagementController;

    private User currentUser;
    private Runnable onLogout;

    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    @FXML
    private void initialize() {
        loadBrandLogo();
        onRefreshClients();
    }

    private void loadBrandLogo() {
        if (brandLogo == null) {
            return;
        }
        Image image = loadLogoImage("/dk/easv/swiftdoc/assets/logos/LogoBlue2H.png");
        if (image == null) {
            image = loadLogoImage("/dk/easv/swiftdoc/assets/logos/WeblagerLightBLue.png");
        }
        brandLogo.setImage(image);
    }

    private Image loadLogoImage(String classpath) {
        try {
            var url = AdminController.class.getResource(classpath);
            return url != null ? new Image(url.toExternalForm()) : null;
        } catch (Exception ex) {
            return null;
        }
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
            if (req == null) return;
            profileService.createProfile(
                    req.profileName(), req.client(), req.duplicateDetectionEnabled(),
                    req.profileRotation(), req.profileBrightness(), req.blackAndWhite());
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

    // ---------------- Session ----------------

    @FXML
    private void onLogoutCommand() {
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
