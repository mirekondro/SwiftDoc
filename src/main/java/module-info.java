module dk.easv.swiftdoc {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.net.http;

    opens dk.easv.swiftdoc.app to javafx.graphics;
    opens dk.easv.swiftdoc.controller to javafx.fxml;
    exports dk.easv.swiftdoc;
}