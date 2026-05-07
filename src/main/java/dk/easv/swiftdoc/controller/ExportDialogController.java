package dk.easv.swiftdoc.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;

public class ExportDialogController {

    public enum ExportMode {
        ACTIVE_SESSION("Active session box"),
        SELECTED_SIDEBAR("Selected sidebar item");

        private final String label;

        ExportMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record ExportRequest(ExportMode mode, File outputDir) {}

    private ExportRequest exportRequest;

    @FXML private DialogPane dialogPane;
    @FXML private ToggleGroup modeToggleGroup;
    @FXML private RadioButton activeSessionRadio;
    @FXML private RadioButton selectedSidebarRadio;
    @FXML private TextField folderTextField;
    @FXML private Label folderErrorLabel;
    @FXML private Label modeHintLabel;

    @FXML private ButtonType exportButtonType;
    @FXML private ButtonType cancelButtonType;

    @FXML
    private void initialize() {
        activeSessionRadio.setUserData(ExportMode.ACTIVE_SESSION);
        selectedSidebarRadio.setUserData(ExportMode.SELECTED_SIDEBAR);
        activeSessionRadio.setSelected(true);
        updateModeHint(ExportMode.ACTIVE_SESSION);

        modeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            updateModeHint(getSelectedMode());
            refreshExportEnabled();
        });

        folderTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFolderError();
            refreshExportEnabled();
        });

        Button exportBtn = (Button) dialogPane.lookupButton(exportButtonType);
        exportBtn.addEventFilter(ActionEvent.ACTION, this::onExport);

        updateFolderError();
        refreshExportEnabled();
    }

    @FXML
    private void onBrowseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose export folder");
        Window owner = dialogPane.getScene() != null ? dialogPane.getScene().getWindow() : null;
        File selected = chooser.showDialog(owner);
        if (selected != null) {
            folderTextField.setText(selected.getAbsolutePath());
        }
    }

    private void onExport(ActionEvent event) {
        exportRequest = null;

        ExportMode mode = getSelectedMode();
        File outputDir = resolveOutputDir();
        if (mode == null || outputDir == null) {
            updateFolderError();
            event.consume();
            return;
        }
        exportRequest = new ExportRequest(mode, outputDir);
    }

    private File resolveOutputDir() {
        String path = folderTextField.getText();
        if (path == null || path.isBlank()) {
            return null;
        }
        File dir = new File(path.trim());
        return dir.isDirectory() ? dir : null;
    }

    private void updateFolderError() {
        String path = folderTextField.getText();
        if (path == null || path.isBlank()) {
            folderErrorLabel.setText("Choose a folder to export into.");
            return;
        }
        File dir = new File(path.trim());
        if (!dir.isDirectory()) {
            folderErrorLabel.setText("Folder must exist and be a directory.");
            return;
        }
        folderErrorLabel.setText("");
    }

    private void refreshExportEnabled() {
        Button exportBtn = (Button) dialogPane.lookupButton(exportButtonType);
        if (exportBtn == null) {
            return;
        }
        exportBtn.setDisable(getSelectedMode() == null || resolveOutputDir() == null);
    }

    private void updateModeHint(ExportMode mode) {
        if (mode == null) {
            modeHintLabel.setText("Select what to export.");
            return;
        }
        switch (mode) {
            case ACTIVE_SESSION -> modeHintLabel.setText("Uses the active scan session box.");
            case SELECTED_SIDEBAR -> modeHintLabel.setText(
                    "Exports the box that contains the current sidebar selection.");
        }
    }

    private ExportMode getSelectedMode() {
        if (modeToggleGroup.getSelectedToggle() == null) {
            return null;
        }
        Object value = modeToggleGroup.getSelectedToggle().getUserData();
        return value instanceof ExportMode ? (ExportMode) value : null;
    }

    public ExportRequest getExportRequest() {
        return exportRequest;
    }
}
