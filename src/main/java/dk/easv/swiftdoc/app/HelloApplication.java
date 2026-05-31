package dk.easv.swiftdoc.app;

import dk.easv.swiftdoc.controller.AdminController;
import dk.easv.swiftdoc.controller.LoginController;
import dk.easv.swiftdoc.controller.MainController;
import dk.easv.swiftdoc.db.DBConnection;
import dk.easv.swiftdoc.model.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;


import java.sql.Connection;
import java.util.Optional;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);
        loadFonts();
        testDatabaseConnectionInBackground();
        launchApp(stage);
    }

    private void loadFonts() {
        String[] weights = {"Regular", "Medium", "SemiBold", "Bold"};
        for (String w : weights) {
            String path = "/dk/easv/swiftdoc/fonts/Montserrat-" + w + ".ttf";
            try (InputStream in = HelloApplication.class.getResourceAsStream(path)) {
                if (in == null) {
                    System.err.println("Warning: font not found: " + path);
                    continue;
                }
                Font.loadFont(in, 14);
            } catch (Exception ex) {
                System.err.println("Warning: failed to load " + path + ": " + ex.getMessage());
            }
        }
    }

    private void launchApp(Stage stage) {
        try {
            Optional<User> loggedIn = showLoginDialog();
            if (loggedIn.isEmpty()) {
                Platform.exit();
                return;
            }

            User user = loggedIn.get();
            Runnable onLogout = () -> {
                stage.hide();
                launchApp(stage);
            };
            if (user.isAdmin()) {
                showAdminScene(stage, user, onLogout);
            } else {
                showUserScene(stage, user, onLogout);
            }
        } catch (Exception ex) {
            showStartupErrorDialog(ex);
            Platform.exit();
        }
    }

    private Optional<User> showLoginDialog() throws Exception {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(
                "/dk/easv/swiftdoc/view/login-view.fxml"));
        Scene loginScene = new Scene(loader.load());
        loginScene.getStylesheets().add(
                HelloApplication.class.getResource("/dk/easv/swiftdoc/view/app.css").toExternalForm());

        Stage loginStage = new Stage();
        loginStage.setTitle("Sign in — SwiftDoc");
        loginStage.setScene(loginScene);
        loginStage.initModality(Modality.APPLICATION_MODAL);
        loginStage.setResizable(false);
        loginStage.showAndWait();

        LoginController controller = loader.getController();
        return controller.getAuthenticatedUser();
    }

    private void showUserScene(Stage stage, User user, Runnable onLogout) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource(
                "/dk/easv/swiftdoc/view/main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(
                HelloApplication.class.getResource("/dk/easv/swiftdoc/view/app.css").toExternalForm());

        MainController controller = fxmlLoader.getController();
        controller.setCurrentUser(user);
        controller.setOnLogout(onLogout);

        stage.setTitle("SwiftDoc — " + user.getUsername());
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private void showAdminScene(Stage stage, User user, Runnable onLogout) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource(
                "/dk/easv/swiftdoc/view/admin-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(
                HelloApplication.class.getResource("/dk/easv/swiftdoc/view/app.css").toExternalForm());

        AdminController controller = fxmlLoader.getController();
        controller.setCurrentUser(user);
        controller.setOnLogout(onLogout);

        stage.setTitle("SwiftDoc Admin — " + user.getUsername());
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private void showStartupErrorDialog(Exception ex) {
        ex.printStackTrace();   // real error goes to console
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Startup Error");
            alert.setHeaderText("SwiftDoc could not be started");
            alert.setContentText("Failed to start the application.\n\n" + ex.getMessage());
            alert.showAndWait();
        } catch (Throwable t) {
            // If even showing the dialog fails, just log — never recurse.
            t.printStackTrace();
        }
    }

    private void testDatabaseConnectionInBackground() {
        Thread dbTestThread = new Thread(() -> {
            try {
                Connection connection = DBConnection.getInstance().getConnection();
                if (connection == null || connection.isClosed()) {
                    System.err.println("Warning: Database connection test failed.");
                }
            } catch (Exception ex) {
                System.err.println("Warning: Failed to connect to database: " + ex.getMessage());
            }
        });
        dbTestThread.setDaemon(true);
        dbTestThread.start();
    }
}
