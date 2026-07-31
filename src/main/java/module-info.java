module com.juanpablo.evermail {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires jakarta.mail;
    requires org.xerial.sqlitejdbc;
    requires io.github.cdimascio.dotenv.java;
    requires java.keyring;
    requires static lombok;

    opens com.juanpablo.evermail to javafx.fxml;
    opens com.juanpablo.evermail.model;

    exports com.juanpablo.evermail;
    exports com.juanpablo.evermail.model;
}