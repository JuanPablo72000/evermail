package com.juanpablo.evermail.ui;

import java.util.Objects;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/** An original design asset in a resizable native layout slot. */
public final class FigmaImage extends Region {
    private final ImageView image = new ImageView();
    private String source;
    private boolean fitHeightOnly;

    public FigmaImage() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        setMouseTransparent(true);
        getChildren().add(image);
    }
    public String getSource() { return source; }
    public boolean isFitHeightOnly() { return fitHeightOnly; }
    public void setFitHeightOnly(boolean value) { fitHeightOnly = value; requestLayout(); }
    public void setSource(String source) {
        this.source = source;
        image.setImage(new Image(Objects.requireNonNull(getClass().getResource(
                "/com/juanpablo/evermail/images/figma/" + source)).toExternalForm()));
        requestLayout();
    }
    @Override protected void layoutChildren() {
        double width = Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset());
        double height = Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset());
        image.setFitWidth(fitHeightOnly ? 0 : width);
        image.setFitHeight(height);
        image.relocate(snappedLeftInset() + (width - image.getBoundsInLocal().getWidth()) / 2,
                snappedTopInset() + (height - image.getBoundsInLocal().getHeight()) / 2);
    }
}
