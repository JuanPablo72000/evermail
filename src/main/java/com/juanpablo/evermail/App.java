package com.juanpablo.evermail;

import java.net.URL;
import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        URL resource = App.class.getResource("/com/juanpablo/evermail/fxml/Login-Screen.fxml");
        Parent root = FXMLLoader.load(Objects.requireNonNull(resource));
        Scene scene = new Scene(root);
        stage.setTitle("Evermail");
        stage.setScene(scene);
        stage.show();
    }
}