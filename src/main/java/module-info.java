module com.juanpablo.evermail.evermail {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.juanpablo.evermail.evermail to javafx.fxml;
    exports com.juanpablo.evermail.evermail;
}