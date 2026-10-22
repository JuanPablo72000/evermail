package com.juanpablo.evermail.ui;

import java.util.Objects;
import javafx.scene.text.Font;

/** Bundled fonts keep FXML, previews and production consistent with Figma. */
final class DesignFonts {
    private static boolean loaded;
    static synchronized void load() {
        if (loaded) return;
        for (String name : new String[]{"Inter-400", "Inter-600", "Inter-700",
                "Poppins-400", "Poppins-500", "Poppins-600", "InriaSerif-400"}) {
            var resource = Objects.requireNonNull(DesignFonts.class.getResource(
                    "/com/juanpablo/evermail/fonts/" + name + ".ttf"));
            Font font = Font.loadFont(resource.toExternalForm(), 14);
            if (font == null)
                throw new IllegalStateException("Unable to load design font " + name);
        }
        loaded = true;
    }
    private DesignFonts() {}
}
