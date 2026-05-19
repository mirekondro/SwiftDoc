package dk.easv.swiftdoc.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

import java.util.function.IntConsumer;

/**
 * Controller for the Custom Rotation dialog (items 9: precise rotation).
 *
 * UX:
 *  - Slider + Spinner are bound to the same value. Drag the slider for
 *    visual control, type in the spinner for exact values.
 *  - As the user drags or types, the parent MainController gets each new
 *    angle via the previewCallback so the viewer rotates live.
 *  - On Apply: the value at that moment becomes the "final" angle and is
 *    available via getFinalAngle().
 *  - On Cancel: getFinalAngle() returns null; the caller should roll back
 *    to whatever rotation the file had before opening the dialog.
 *
 * The dialog accepts -359..+359 in the spinner (negatives = counter-clockwise)
 * but the value returned is always normalized to 0..359.
 */
public class CustomRotationDialogController {

    @FXML private DialogPane dialogPane;
    @FXML private Slider angleSlider;
    @FXML private Spinner<Integer> angleSpinner;
    @FXML private ButtonType applyButtonType;
    @FXML private ButtonType cancelButtonType;

    private Integer finalAngle;
    private IntConsumer previewCallback = a -> {};
    private boolean syncing;

    @FXML
    private void initialize() {
        // Spinner accepts negative values for convenience; value is normalized
        // before being applied.
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(-359, 359, 0, 1);
        angleSpinner.setValueFactory(factory);

        // Slider drags → update spinner and fire preview
        angleSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncing) return;
            int normalized = normalize(newVal.intValue());
            syncing = true;
            try {
                angleSpinner.getValueFactory().setValue(normalized);
            } finally {
                syncing = false;
            }
            previewCallback.accept(normalized);
        });

        // Spinner types → update slider and fire preview
        angleSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncing || newVal == null) return;
            int normalized = normalize(newVal);
            syncing = true;
            try {
                angleSlider.setValue(normalized);
            } finally {
                syncing = false;
            }
            previewCallback.accept(normalized);
        });

        // Defer button wiring until the buttons exist in the DialogPane.
        // event filter so we can capture the final value before the dialog
        // closes.
        javafx.application.Platform.runLater(() -> {
            Button applyBtn = (Button) dialogPane.lookupButton(applyButtonType);
            if (applyBtn != null) {
                applyBtn.addEventFilter(ActionEvent.ACTION, this::onApply);
            }
        });
    }

    /**
     * Configure where the dialog should send live previews of the current
     * angle as the user drags / types. Called by MainController immediately
     * after loading the FXML.
     *
     * @param startingAngle the angle the file currently has (used to seed
     *                      the slider/spinner so the dialog opens at the
     *                      file's existing rotation)
     * @param previewCallback called with every new angle, on the FX thread.
     *                        MainController uses this to spin the viewer live.
     */
    public void configure(int startingAngle, IntConsumer previewCallback) {
        this.previewCallback = previewCallback != null ? previewCallback : a -> {};
        int normalized = normalize(startingAngle);
        syncing = true;
        try {
            angleSlider.setValue(normalized);
            angleSpinner.getValueFactory().setValue(normalized);
        } finally {
            syncing = false;
        }
    }

    private void onApply(ActionEvent event) {
        Integer value = angleSpinner.getValue();
        finalAngle = value != null ? normalize(value) : 0;
    }

    /**
     * @return the angle the user picked when they clicked Apply, normalized
     *         to 0..359; null if the dialog was cancelled or closed.
     */
    public Integer getFinalAngle() {
        return finalAngle;
    }

    /** Normalize any integer to the 0..359 range, including negatives. */
    private static int normalize(int degrees) {
        int mod = degrees % 360;
        return mod < 0 ? mod + 360 : mod;
    }
}
