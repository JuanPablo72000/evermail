module com.juanpablo.evermail.evermail {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires jakarta.mail;
    requires org.xerial.sqlitejdbc;
    requires io.github.cdimascio.dotenv.java;
    requires java.keyring;

    opens com.juanpablo.evermail.evermail to javafx.fxml;
    exports com.juanpablo.evermail.evermail;
}