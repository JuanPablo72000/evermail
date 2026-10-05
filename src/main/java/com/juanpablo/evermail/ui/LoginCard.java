package com.juanpablo.evermail.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Native frosted backdrop. The controls remain sharp above the blurred background. */
public final class LoginCard extends VBox {
    private final ImageView backdrop = new ImageView(new Image(LoginCard.class.getResource(
            "/com/juanpablo/evermail/images/figma/loginDesign-imgLowPolyGridHaikei1.png").toExternalForm()));
    private final Rectangle tint = new Rectangle();
    private final Rectangle clip = new Rectangle();
    private final Pane glass = new Pane(backdrop, tint);

    public LoginCard() {
        // Region's sizing sentinel belongs in the API, not a CSS length.
        setMaxHeight(USE_PREF_SIZE);
        backdrop.setEffect(new GaussianBlur(8));
        tint.setFill(Color.rgb(37, 26, 48, 0.8));
        clip.setArcWidth(36); clip.setArcHeight(36);
        glass.setClip(clip); glass.setManaged(false); glass.setMouseTransparent(true);
        getChildren().add(glass);
    }
    @Override protected void layoutChildren() {
        super.layoutChildren();
        Node ancestor = getParent();
        while (ancestor != null && !(ancestor instanceof ScrollPane)) ancestor = ancestor.getParent();
        if (!(ancestor instanceof ScrollPane viewport)) return;
        Bounds frame = viewport.localToScene(viewport.getLayoutBounds());
        Bounds card = localToScene(getLayoutBounds());
        double factor = Math.max(frame.getWidth() / 960, frame.getHeight() / 540);
        backdrop.setFitWidth(960 * factor); backdrop.setFitHeight(540 * factor);
        backdrop.relocate(frame.getMinX() - card.getMinX() + (frame.getWidth() - 960 * factor) / 2,
                frame.getMinY() - card.getMinY() + (frame.getHeight() - 540 * factor) / 2);
        glass.resize(getWidth(), getHeight());
        tint.setWidth(getWidth()); tint.setHeight(getHeight());
        clip.setWidth(getWidth()); clip.setHeight(getHeight());
    }
}
