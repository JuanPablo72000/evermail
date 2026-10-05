package com.juanpablo.evermail.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

/** Presentation-only sizing. It does not create sessions, call services or send mail. */
public final class ViewController {
    @FXML private ScrollPane viewport;
    @FXML private Region layout;
    @FXML private void initialize() {
        DesignFonts.load();
        viewport.viewportBoundsProperty().addListener((o, before, bounds) -> {
            if (viewport.getStyleClass().contains("main-view") || viewport.getStyleClass().contains("reader-view")) {
                double scale = Math.max(2.0 / 3, Math.min(1, bounds.getWidth() / 1920));
                viewport.setStyle("-fx-font-size: " + (20 * scale) + "px;");
            }
            layout.setPrefWidth(bounds.getWidth());
            // Keep the desktop composition filling the window while preserving the grid's minimum height.
            layout.setPrefHeight(bounds.getHeight());
        });
    }
}
