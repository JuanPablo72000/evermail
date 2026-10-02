package com.juanpablo.evermail.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

/** Presentation-only sizing. It does not create sessions, call services or send mail. */
public final class ViewController {
    @FXML private ScrollPane viewport;
    @FXML private Region layout;
    @FXML private void initialize() {
        viewport.viewportBoundsProperty().addListener((o, before, bounds) -> {
            layout.setPrefWidth(bounds.getWidth());
            // Keep the desktop composition filling the window while preserving the grid's minimum height.
            layout.setPrefHeight(bounds.getHeight());
        });
    }
}
