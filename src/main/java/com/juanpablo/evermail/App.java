package com.juanpablo.evermail;

import java.net.URL;
import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.juanpablo.evermail.ui.PreviewData;
import com.juanpablo.evermail.ui.MailWindow;
import com.juanpablo.evermail.application.ApplicationCoordinator;
import com.juanpablo.evermail.presentation.CoordinatedPresentationBackend;
import javafx.application.Platform;

public class App extends Application {
    private MailWindow window;
    @Override
    public void start(Stage stage) throws Exception {
        String preview = getParameters().getNamed().get("preview");
        if (preview == null) {
            window = new MailWindow(stage, new CoordinatedPresentationBackend(new ApplicationCoordinator()),
                    uri -> getHostServices().showDocument(uri.toString()), Platform::exit);
            Platform.setImplicitExit(false);
            stage.setScene(new Scene(window.root(), 1280, 720));
            stage.setTitle("Evermail"); stage.setMinWidth(360); stage.setMinHeight(360);
            stage.show(); window.start();
            return;
        }
        String screen = PreviewData.screen(preview);
        URL resource = App.class.getResource("/com/juanpablo/evermail/fxml/" + screen);
        Parent root = FXMLLoader.load(Objects.requireNonNull(resource));
        if (preview != null) PreviewData.populate(root);
        Scene scene = new Scene(root);
        stage.setTitle("Evermail");
        stage.setScene(scene);
        stage.setMinWidth(360);
        stage.setMinHeight(360);
        stage.show();
    }

    @Override public void stop() {
        if (window != null) window.close();
    }
}
