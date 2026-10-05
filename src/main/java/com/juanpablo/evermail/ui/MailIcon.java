package com.juanpablo.evermail.ui;

import javafx.scene.layout.StackPane;

/** Original Figma vectors, rasterized at 3x for JavaFX ImageView. SVG sources are retained. */
public final class MailIcon extends StackPane {
    private final FigmaImage image = new FigmaImage();
    private String kind = "inbox";
    public MailIcon() {
        getStyleClass().add("mail-icon");
        setMouseTransparent(true);
        getChildren().add(image);
        setKind(kind);
    }
    public String getKind() { return kind; }
    public void setKind(String value) {
        kind = value;
        String file = switch (value) {
            case "notes" -> "8-282-imgNotas";
            case "calendar" -> "8-282-imgCalendario";
            case "send" -> "8-282-imgSend";
            case "draft" -> "8-282-imgGroup2";
            case "logout" -> "8-282-imgGroup";
            case "search" -> "8-282-imgVector";
            case "refresh" -> "8-282-imgGroup1";
            case "back" -> "48-66-imgArrowLeft";
            case "previous", "next" -> "48-66-imgFlecha";
            case "reply" -> "48-66-imgIconos";
            case "forward" -> "48-66-imgIconos1";
            case "delete" -> "48-66-imgIconos2";
            case "archive" -> "48-66-imgIconos3";
            default -> "8-282-imgBandeja";
        };
        image.setSource(file + ".png");
        image.setRotate(value.equals("next") ? 180 : 0);
    }
}
