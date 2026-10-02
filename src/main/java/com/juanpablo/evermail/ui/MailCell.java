package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.model.MailHeader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

/** Reuses the authored row; no decrypted body is fetched just to draw an inbox row. */
final class MailCell extends ListCell<MailHeader> {
    private final Region row;
    private final Label avatar, sender, subject, preview, date;
    MailCell() {
        try {
            row = FXMLLoader.load(getClass().getResource("/com/juanpablo/evermail/fxml/Mail-Row.fxml"));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        avatar = (Label) row.lookup("#avatar"); sender = (Label) row.lookup("#sender");
        subject = (Label) row.lookup("#subject"); preview = (Label) row.lookup("#preview");
        date = (Label) row.lookup("#date"); row.prefWidthProperty().bind(widthProperty());
    }
    @Override protected void updateItem(MailHeader mail, boolean empty) {
        super.updateItem(mail, empty); setText(null);
        if (empty || mail == null) {
            for (Label label : new Label[]{avatar,sender,subject,preview,date}) label.setText("");
            setGraphic(null); return;
        }
        String name = mail.getSenderName() == null || mail.getSenderName().isBlank() ? mail.getSenderEmail() : mail.getSenderName();
        sender.setText(name); avatar.setText(MailWindow.initial(name));
        subject.setText(mail.getSubject().isBlank() ? "(Sin asunto)" : mail.getSubject());
        preview.setText(mail.isRead() ? "Leído" : "Sin leer");
        date.setText(mail.getOccurredAt() == null ? "" : DateTimeFormatter.ofPattern("dd/MM HH:mm")
                .withZone(ZoneId.systemDefault()).format(mail.getOccurredAt()));
        row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("read"), mail.isRead());
        setGraphic(row);
    }
}
