module com.juanpablo.evermail {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires jakarta.mail;
    requires org.xerial.sqlitejdbc;
    requires io.github.cdimascio.dotenv.java;
    requires java.keyring;
    requires com.google.gson;  // NUEVO - para parsear JSON de token response

    requires java.net.http;      // NUEVO - HttpClient para OAuth
    requires java.desktop;       // NUEVO - Desktop.browse() para abrir navegador
    requires jdk.httpserver;     // NUEVO - HttpServer para loopback callback

    requires static lombok;

    opens com.juanpablo.evermail to javafx.fxml;
    opens com.juanpablo.evermail.model;

    exports com.juanpablo.evermail;
    exports com.juanpablo.evermail.model;
}