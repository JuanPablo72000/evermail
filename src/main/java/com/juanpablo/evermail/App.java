package com.juanpablo.evermail;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(new Label("Evermail"));
        Scene scene = new Scene(root, 320, 240);
        stage.setTitle("Evermail");
        stage.setScene(scene);
        stage.show();
    }
}