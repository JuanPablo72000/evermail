package com.juanpablo.evermail.ui;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Local scalable line icons. Paths describe glyph geometry, not screen positioning. */
public final class MailIcon extends StackPane {
    private String kind = "inbox";
    public MailIcon() { getStyleClass().add("mail-icon"); setMouseTransparent(true); setKind(kind); }
    public String getKind() { return kind; }
    public void setKind(String value) {
        kind = value;
        String path = switch (value) {
            case "notes" -> "M4 2 H20 V22 H4 Z M8 7 H16 M8 11 H16 M8 15 H13";
            case "calendar" -> "M3 5 H21 V22 H3 Z M3 10 H21 M8 2 V7 M16 2 V7 M8 13 V19 M13 13 V19 M18 13 V19";
            case "send" -> "M2 11 L22 2 L15 22 L10 14 Z M10 14 L22 2";
            case "draft" -> "M6 2 H15 L20 7 V22 H6 Z M15 2 V7 H20";
            case "logout" -> "M10 3 H21 V21 H10 M14 12 H2 M7 7 L2 12 L7 17";
            case "search" -> "M17 10 A7 7 0 1 1 3 10 A7 7 0 1 1 17 10 M15 15 L22 22";
            case "refresh" -> "M21 5 V11 H15 M20 10 A8 8 0 1 0 18 19 M21 11 L18 6";
            case "back" -> "M21 12 H3 M10 5 L3 12 L10 19";
            case "previous" -> "M15 3 L7 12 L15 21";
            case "next" -> "M8 3 L16 12 L8 21";
            case "reply" -> "M10 3 L2 10 L10 17 M2 10 H15 C25 10 25 22 15 22";
            case "forward" -> "M3 2 H12 L22 12 L12 22 H3 L12 12 Z";
            case "delete" -> "M3 6 H21 M8 6 V2 H16 V6 M5 6 L7 22 H17 L19 6 M10 10 V18 M14 10 V18";
            case "archive" -> "M3 3 H21 V7 H3 Z M5 7 V22 H19 V7 M9 11 H15";
            default -> "M3 3 H21 V21 H3 Z M3 14 H8 L10 17 H14 L16 14 H21";
        };
        SVGPath glyph = new SVGPath(); glyph.setContent(path); glyph.getStyleClass().add("icon-stroke");
        getChildren().setAll(new Group(glyph));
        setMinSize(24, 24); setPrefSize(24, 24); setMaxSize(24, 24);
    }
}
