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
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.util.Optional;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);
        testDatabaseConnectionInBackground();
        launchApp(stage);
    }

    private void launchApp(Stage stage) {
        try {
            Optional<User> loggedIn = showLoginDialog();
            if (loggedIn.isEmpty()) {
                Platform.exit();
                return;
            }

            User user = loggedIn.get();
            if (user.isAdmin()) {
                showAdminScene(stage, user);
            } else {
                showUserScene(stage, user, () -> {
                    stage.hide();
                    launchApp(stage);
                });
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

    private void showAdminScene(Stage stage, User user) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource(
                "/dk/easv/swiftdoc/view/admin-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(
                HelloApplication.class.getResource("/dk/easv/swiftdoc/view/app.css").toExternalForm());

        AdminController controller = fxmlLoader.getController();
        controller.setCurrentUser(user);

        stage.setTitle("SwiftDoc Admin — " + user.getUsername());
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private void showStartupErrorDialog(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                "Failed to start the application.\n\n" + ex.getMessage(), ButtonType.OK);
        alert.setTitle("Startup Error");
        alert.setHeaderText("SwiftDoc could not be started");
        alert.showAndWait();
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
