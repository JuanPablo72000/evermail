module com.juanpablo.evermail {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires jakarta.mail;
    requires org.xerial.sqlitejdbc;
    requires io.github.cdimascio.dotenv.java;
    requires java.keyring;

    opens com.juanpablo.evermail to javafx.fxml;
    exports com.juanpablo.evermail;
}