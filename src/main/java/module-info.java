module dk.easv.swiftdoc {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;
    requires java.net.http;
    requires java.sql;
    requires com.google.zxing;
    requires com.google.zxing.javase;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    opens dk.easv.swiftdoc.app to javafx.graphics;
    opens dk.easv.swiftdoc.controller to javafx.fxml;
    exports dk.easv.swiftdoc;
    exports dk.easv.swiftdoc.db;
}