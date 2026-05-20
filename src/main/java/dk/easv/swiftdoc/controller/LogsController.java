package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.dal.LogDAO;
import dk.easv.swiftdoc.model.LogEntry;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogsController {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogDAO logDAO = new LogDAO();

    @FXML private TableView<LogEntry>            logsTable;
    @FXML private TableColumn<LogEntry, String>  timestampCol;
    @FXML private TableColumn<LogEntry, String>  userCol;
    @FXML private TableColumn<LogEntry, String>  actionCol;
    @FXML private TableColumn<LogEntry, Integer> fileIdCol;
    @FXML private TableColumn<LogEntry, Integer> documentCol;

    @FXML
    private void initialize() {
        timestampCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTimestamp().format(FORMATTER)));
        userCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getUsername()));
        actionCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getAction().name()));
        fileIdCol.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getFileId()).asObject());
        documentCol.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getDocumentId()).asObject());

        Platform.runLater(this::onRefresh);
    }

    @FXML
    private void onRefresh() {
        try {
            List<LogEntry> entries = logDAO.getAll();
            logsTable.setItems(FXCollections.observableArrayList(entries));
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not load logs");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }
}
