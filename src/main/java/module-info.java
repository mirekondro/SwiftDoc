module dk.easv.swiftdoc {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;
    requires java.net.http;
    requires java.sql;
    requires com.google.zxing;
    requires com.google.zxing.javase;

    opens dk.easv.swiftdoc.app to javafx.graphics;
    opens dk.easv.swiftdoc.controller to javafx.fxml;
    exports dk.easv.swiftdoc;
}