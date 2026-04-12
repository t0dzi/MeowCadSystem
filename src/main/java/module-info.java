module org.example.cadsystem {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;

    opens org.example.cadsystem to javafx.fxml;

    exports org.example.cadsystem;
    exports org.example.model;
    exports org.example.view;
    exports org.example.controller;
    exports org.example.export;
}