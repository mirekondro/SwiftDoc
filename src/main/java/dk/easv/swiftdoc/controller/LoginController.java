package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.Optional;

public class LoginController {

    private final AuthService authService = new AuthService();

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private User authenticatedUser;

    @FXML
    private void onLogin() {
        errorLabel.setText("");
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            errorLabel.setText("Enter username and password.");
            return;
        }
        try {
            Optional<User> result = authService.login(username, password);
            if (result.isEmpty()) {
                errorLabel.setText("Invalid username or password.");
                return;
            }
            authenticatedUser = result.get();
            closeWindow();
        } catch (SQLException ex) {
            System.err.println("[LOGIN] SQL error: " + ex.getMessage());
            ex.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Login error");
            alert.setHeaderText("Database error during login");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            errorLabel.setText("Database error — see console.");
        }
    }

    @FXML
    private void onCancel() {
        authenticatedUser = null;
        closeWindow();
    }

    @FXML
    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            onCancel();
        }
    }

    public Optional<User> getAuthenticatedUser() {
        return Optional.ofNullable(authenticatedUser);
    }

    private void closeWindow() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }
}
