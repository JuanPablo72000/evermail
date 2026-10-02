package com.juanpablo.evermail.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Explicit --preview mode only. No mail, credentials, database or network are accessed. */
public final class PreviewData {
    private PreviewData() {}
    public static String screen(String name) {
        if (name == null) return "Login-Screen.fxml";
        return switch (name) {
            case "login" -> "Login-Screen.fxml";
            case "loading" -> "Loading-Screen.fxml";
            case "loaded" -> "Loading-Complete.fxml";
            case "main" -> "Main-Screen.fxml";
            case "reader" -> "Reader-Screen.fxml";
            case "logout" -> "Logout-Dialog.fxml";
            default -> throw new IllegalArgumentException("Preview: login, loading, loaded, main, reader, logout");
        };
    }
    public static void populate(Parent root) throws IOException {
        if (root instanceof ScrollPane viewport) root = (Parent) viewport.getContent();
        if (root.lookup("#mailList") instanceof ListView<?> untyped) {
            @SuppressWarnings("unchecked") ListView<String> list = (ListView<String>) untyped;
            list.getItems().setAll(java.util.Collections.nCopies(50, "Correo de ejemplo"));
            list.setCellFactory(view -> new ListCell<>() {
                private final Parent row = loadRow();
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(null); setGraphic(empty ? null : row);
                }
                private Parent loadRow() {
                    try {
                        Region row = FXMLLoader.load(PreviewData.class.getResource("/com/juanpablo/evermail/fxml/Mail-Row.fxml"));
                        row.prefWidthProperty().bind(widthProperty());
                        return row;
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                }
            });
            ((Label) root.lookup("#mailCount")).setText("50 Correos Cargados");
            ((TextField) root.lookup("#fromField")).setText("juanpablodev@gmail.com");
            ((TextField) root.lookup("#subjectField")).setText("Revision Interfaz");
        }
        if (root.lookup("#bodyHost") instanceof StackPane host) {
            Parent formatted = FXMLLoader.load(PreviewData.class.getResource("/com/juanpablo/evermail/fxml/Reader-Sample.fxml"));
            TextArea plain = (TextArea) root.lookup("#plainBody");
            plain.setText("Evermail · Revisión de interfaz\n\nHola, Ana:\n\nTe comparto un resumen de los puntos principales que revisamos sobre la interfaz de Evermail.\nCreo que vamos por buen camino y estas son las propuestas acordadas:\n\nAcceso: Fondo geométrico y autorización guiada\nBandeja: Lista de correos y envío en paralelo\nLectura: Contenido con formato y navegación\n\nGracias por tus comentarios.\nJuan Pablo");
            host.getChildren().setAll(formatted);
            ((Button) root.lookup("#textButton")).setOnAction(e -> host.getChildren().setAll(plain));
            ((Button) root.lookup("#formattedButton")).setOnAction(e -> host.getChildren().setAll(formatted));
        }
    }
}
