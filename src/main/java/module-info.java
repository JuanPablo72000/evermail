module com.juanpablo.evermail {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.jsoup;
    requires com.helger.css;
    requires com.helger.commons;
    // ph-css/ph-commons are automatic modules and cannot declare this named runtime dependency.
    requires org.slf4j;
    requires java.xml;

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
    opens com.juanpablo.evermail.ui to javafx.fxml;
    exports com.juanpablo.evermail.ui;
    opens com.juanpablo.evermail.model;
    opens com.juanpablo.evermail.repository to com.google.gson;

    exports com.juanpablo.evermail;
    exports com.juanpablo.evermail.model;
}
