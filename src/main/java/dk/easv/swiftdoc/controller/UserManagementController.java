package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.dal.ProfileDAO;
import dk.easv.swiftdoc.dal.UserProfileAccessDAO;
import dk.easv.swiftdoc.model.ScanningProfile;
import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.model.User.Role;
import dk.easv.swiftdoc.service.AuthService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserManagementController {

    private final AuthService authService = new AuthService();
    private final ProfileDAO profileDAO = new ProfileDAO();
    private final UserProfileAccessDAO accessDAO = new UserProfileAccessDAO();

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colActive;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<Role> roleCombo;
    @FXML private Label formTitleLabel;
    @FXML private Label messageLabel;
    @FXML private Button deactivateButton;
    @FXML private VBox profileAccessSection;
    @FXML private VBox profileCheckboxContainer;

    private User currentAdmin;
    private User editingUser;

    @FXML
    private void initialize() {
        colUsername.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getUsername()));
        colRole.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRole().name()));
        colActive.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActive() ? "Active" : "Inactive"));

        colActive.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("Active".equals(item)
                        ? "-fx-text-fill: #2a7a2a; -fx-font-weight: bold;"
                        : "-fx-text-fill: #cc0000; -fx-font-weight: bold;");
            }
        });

        usersTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setOpacity(empty || user == null || user.isActive() ? 1.0 : 0.5);
            }
        });

        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        roleCombo.setItems(FXCollections.observableArrayList(Role.USER, Role.ADMIN));

        usersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> onSelectionChanged(sel));

        clearForm();
        loadUsers();
    }

    public void setCurrentAdmin(User admin) {
        this.currentAdmin = admin;
    }

    private void loadUsers() {
        try {
            List<User> users = authService.listUsers();
            usersTable.setItems(FXCollections.observableArrayList(users));
        } catch (SQLException ex) {
            showMessage("Could not load users: " + ex.getMessage(), true);
        }
    }

    private void onSelectionChanged(User selected) {
        if (selected == null) return;
        deactivateButton.setText(selected.isActive() ? "Deactivate" : "Activate");
    }

    @FXML
    private void onCreateClicked() {
        editingUser = null;
        clearForm();
        formTitleLabel.setText("Create user");
        usernameField.requestFocus();
    }

    @FXML
    private void onEditClicked() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a user first.", false); return; }
        editingUser = selected;
        formTitleLabel.setText("Edit — " + selected.getUsername());
        usernameField.setText(selected.getUsername());
        passwordField.clear();
        roleCombo.getSelectionModel().select(selected.getRole());
        messageLabel.setText("");
        showProfileAccess(selected.getUserId());
    }

    @FXML
    private void onSaveClicked() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        Role role = roleCombo.getSelectionModel().getSelectedItem();

        if (username == null || username.isBlank()) { showMessage("Username is required.", true); return; }
        if (role == null) { showMessage("Select a role.", true); return; }

        try {
            if (editingUser == null) {
                if (password == null || password.isEmpty()) { showMessage("Password is required for new users.", true); return; }
                authService.createUser(username, password, role);
                showMessage("User '" + username + "' created.", false);
            } else {
                authService.updateUser(editingUser.getUserId(), username, password.isEmpty() ? null : password, role);
                Set<Integer> selectedProfileIds = profileCheckboxContainer.getChildren().stream()
                        .filter(n -> n instanceof CheckBox)
                        .map(n -> (CheckBox) n)
                        .filter(CheckBox::isSelected)
                        .map(cb -> (Integer) cb.getUserData())
                        .collect(Collectors.toSet());
                accessDAO.setProfilesForUser(editingUser.getUserId(), selectedProfileIds);
                showMessage("User '" + username + "' updated.", false);
            }
            clearForm();
            loadUsers();
        } catch (IllegalArgumentException | SQLException ex) {
            showMessage(ex.getMessage(), true);
        }
    }

    @FXML
    private void onDeactivateClicked() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a user first.", false); return; }
        if (isSelf(selected)) { showMessage("Cannot deactivate your own account.", true); return; }
        try {
            boolean newActive = !selected.isActive();
            authService.setUserActive(selected.getUserId(), newActive);
            showMessage("User '" + selected.getUsername() + "' " + (newActive ? "activated." : "deactivated."), false);
            loadUsers();
        } catch (SQLException ex) {
            showMessage("Could not update user: " + ex.getMessage(), true);
        }
    }

    @FXML
    private void onDeleteClicked() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showMessage("Select a user first.", false); return; }
        if (isSelf(selected)) { showMessage("Cannot delete your own account.", true); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete user '" + selected.getUsername() + "'?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Confirm delete");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                authService.deleteUser(selected.getUserId());
                clearForm();
                loadUsers();
                showMessage("User deleted.", false);
            } catch (SQLException ex) {
                showMessage("Could not delete user: " + ex.getMessage(), true);
            }
        });
    }

    @FXML
    private void onCancelClicked() {
        clearForm();
    }

    private void clearForm() {
        editingUser = null;
        usernameField.clear();
        passwordField.clear();
        roleCombo.getSelectionModel().select(Role.USER);
        formTitleLabel.setText("Select a user or click Create");
        messageLabel.setText("");
        profileCheckboxContainer.getChildren().clear();
        profileAccessSection.setVisible(false);
        profileAccessSection.setManaged(false);
    }

    private void showProfileAccess(int userId) {
        profileCheckboxContainer.getChildren().clear();
        try {
            List<ScanningProfile> allProfiles = profileDAO.getAll();
            Set<Integer> granted = accessDAO.getProfileIdsForUser(userId);
            if (allProfiles.isEmpty()) {
                profileCheckboxContainer.getChildren().add(new Label("No profiles exist yet."));
            } else {
                for (ScanningProfile p : allProfiles) {
                    CheckBox cb = new CheckBox(p.toString());
                    cb.setUserData(p.getProfileId());
                    cb.setSelected(granted.contains(p.getProfileId()));
                    profileCheckboxContainer.getChildren().add(cb);
                }
            }
        } catch (SQLException ex) {
            profileCheckboxContainer.getChildren().add(new Label("Could not load profiles: " + ex.getMessage()));
        }
        profileAccessSection.setVisible(true);
        profileAccessSection.setManaged(true);
    }

    private boolean isSelf(User user) {
        return currentAdmin != null && user.getUserId() == currentAdmin.getUserId();
    }

    private void showMessage(String msg, boolean isError) {
        messageLabel.setStyle(isError ? "-fx-text-fill: #cc0000;" : "-fx-text-fill: #2a7a2a;");
        messageLabel.setText(msg);
    }
}
