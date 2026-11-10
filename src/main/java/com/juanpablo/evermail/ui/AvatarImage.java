package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.model.AvatarPhoto;
import java.io.ByteArrayInputStream;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.*;
import javafx.scene.shape.Circle;

/** Renders only backend-validated bytes, never a network URL. */
final class AvatarImage {
    private static final String PHOTO = AvatarImage.class.getName();
    static void show(Label label, String name, AvatarPhoto photo) {
        String explanation = photo == null ? null : switch (photo.status()) {
            case AVAILABLE -> "Foto del remitente";
            case ABSENT -> "Google no ofrece una foto para esta dirección en tus contactos.";
            case PERMISSION_REQUIRED -> "Vuelve a autorizar el acceso a tus contactos para mostrar las fotos disponibles.";
            case UNAVAILABLE -> "No se pudo consultar la foto. Actualiza la bandeja para volver a intentarlo.";
        };
        if (explanation == null) label.setTooltip(null);
        else if (label.getTooltip() == null || !explanation.equals(label.getTooltip().getText()))
            label.setTooltip(new Tooltip(explanation));
        if (photo == null || photo.status() != AvatarPhoto.Status.AVAILABLE) {
            label.getProperties().remove(PHOTO);
            label.setGraphic(null);
            label.setText(name == null ? "" : MailWindow.initial(name));
            return;
        }
        if (label.getProperties().get(PHOTO) == photo) return;
        Image image = new Image(new ByteArrayInputStream(photo.png()));
        if (image.isError()) { show(label, name, null); return; }
        ImageView view = new ImageView(image);
        var size = Bindings.createDoubleBinding(() -> Math.max(1, Math.min(label.getWidth(), label.getHeight())),
                label.widthProperty(), label.heightProperty());
        view.fitWidthProperty().bind(size); view.fitHeightProperty().bind(size);
        view.setSmooth(true);
        Circle clip = new Circle();
        clip.centerXProperty().bind(size.divide(2)); clip.centerYProperty().bind(size.divide(2));
        clip.radiusProperty().bind(size.divide(2)); view.setClip(clip);
        label.setGraphic(view); label.setText(""); label.getProperties().put(PHOTO, photo);
    }
}
