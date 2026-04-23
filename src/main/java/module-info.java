module dk.easv.swiftdoc {
    requires javafx.controls;
    requires javafx.fxml;

    opens dk.easv.swiftdoc.controller to javafx.fxml;
    exports dk.easv.swiftdoc;
}