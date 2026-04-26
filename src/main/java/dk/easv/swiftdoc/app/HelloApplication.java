package dk.easv.swiftdoc.app;

import dk.easv.swiftdoc.db.DBConnection;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        try {
            testDatabaseConnection();

            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("/dk/easv/swiftdoc/view/main-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 320, 240);
            stage.setTitle("Hello!");
            stage.setScene(scene);
            stage.show();
        } catch (Exception ex) {
            showStartupErrorDialog(ex);
            Platform.exit();
        }
    }

    private void showStartupErrorDialog(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to start the application.\n\n" + ex.getMessage(), ButtonType.OK);
        alert.setTitle("Startup Error");
        alert.setHeaderText("WebLager could not be started");
        alert.showAndWait();
    }

    private void testDatabaseConnection() throws Exception {
        Connection connection = DBConnection.getInstance().getConnection();
        if (connection == null || connection.isClosed()) {
            throw new IllegalStateException("Database connection test failed.");
        }
    }
}
