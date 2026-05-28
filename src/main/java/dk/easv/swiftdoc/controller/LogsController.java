package dk.easv.swiftdoc.controller;

import dk.easv.swiftdoc.dal.LogDAO;
import dk.easv.swiftdoc.model.LogEntry;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDate;
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
    @FXML private TextField                      searchField;
    @FXML private ComboBox<LogEntry.Action>      actionFilter;
    @FXML private DatePicker                     fromDate;
    @FXML private DatePicker                     toDate;
    @FXML private Label                          countLabel;

    private final ObservableList<LogEntry> masterList = FXCollections.observableArrayList();
    private FilteredList<LogEntry> filteredList;

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

        actionFilter.setItems(FXCollections.observableArrayList(LogEntry.Action.values()));

        filteredList = new FilteredList<>(masterList, e -> true);
        logsTable.setItems(filteredList);

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        actionFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
        fromDate.valueProperty().addListener((obs, o, n) -> applyFilters());
        toDate.valueProperty().addListener((obs, o, n) -> applyFilters());

        Platform.runLater(this::onRefresh);
    }

    @FXML
    private void onRefresh() {
        try {
            List<LogEntry> entries = logDAO.getAll();
            masterList.setAll(entries);
            applyFilters();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not load logs");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void onClearFilters() {
        searchField.clear();
        actionFilter.setValue(null);
        fromDate.setValue(null);
        toDate.setValue(null);
    }

    private void applyFilters() {
        String search = searchField.getText().trim().toLowerCase();
        LogEntry.Action selectedAction = actionFilter.getValue();
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();

        filteredList.setPredicate(entry -> {
            if (!search.isEmpty() && !entry.getUsername().toLowerCase().contains(search))
                return false;
            if (selectedAction != null && entry.getAction() != selectedAction)
                return false;
            LocalDate entryDate = entry.getTimestamp().toLocalDate();
            if (from != null && entryDate.isBefore(from))
                return false;
            if (to != null && entryDate.isAfter(to))
                return false;
            return true;
        });

        countLabel.setText(filteredList.size() + " of " + masterList.size() + " entries");
    }
}
