package com.juanpablo.evermail.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Optional recipients are revealed only by an explicit action, in live and preview views. */
public final class ComposeController {
    @FXML private VBox compose;
    @FXML private Label ccLabel, bccLabel, subjectLabel;
    @FXML private TextField ccField, bccField, subjectField;
    @FXML private Button ccButton, bccButton;
    private final BooleanProperty ccExpanded = new SimpleBooleanProperty(false);
    private final BooleanProperty bccExpanded = new SimpleBooleanProperty(false);

    @FXML private void initialize() {
        compose.getProperties().put(ComposeController.class, this);
        bindVisibility(ccLabel, ccExpanded); bindVisibility(ccField, ccExpanded);
        bindVisibility(bccLabel, bccExpanded); bindVisibility(bccField, bccExpanded);
        ccExpanded.addListener((o, before, after) -> arrange());
        bccExpanded.addListener((o, before, after) -> arrange());
        configureToggle(ccButton, ccField, ccExpanded, "CC");
        configureToggle(bccButton, bccField, bccExpanded, "CCO");
        arrange();
    }

    private static void configureToggle(Button button, TextField field, BooleanProperty expanded, String label) {
        button.setOnAction(e -> {
            expanded.set(!expanded.get());
            if (expanded.get()) field.requestFocus(); else button.requestFocus();
        });
        // Collapsing preserves the draft; make retained recipients visible on the button.
        button.textProperty().bind(Bindings.createStringBinding(
                () -> label + (!expanded.get() && !field.getText().isBlank() ? " •" : ""),
                expanded, field.textProperty()));
        var description = Bindings.createStringBinding(() -> (expanded.get() ? "Ocultar " : "Mostrar ")
                + label + (!expanded.get() && !field.getText().isBlank() ? " (destinatarios conservados)" : ""),
                expanded, field.textProperty());
        button.accessibleTextProperty().bind(description);
        Tooltip tooltip = new Tooltip(); tooltip.textProperty().bind(description); button.setTooltip(tooltip);
        button.disableProperty().bind(field.editableProperty().not());
    }

    private static void bindVisibility(Node node, BooleanProperty expanded) {
        node.visibleProperty().bind(expanded);
        node.managedProperty().bind(expanded);
    }

    private void arrange() {
        int row = 3;
        GridPane.setRowIndex(ccLabel, row); GridPane.setRowIndex(ccField, row);
        if (ccExpanded.get()) row++;
        GridPane.setRowIndex(bccLabel, row); GridPane.setRowIndex(bccField, row);
        if (bccExpanded.get()) row++;
        GridPane.setRowIndex(subjectLabel, row); GridPane.setRowIndex(subjectField, row);
    }

    void reset() { ccExpanded.set(false); bccExpanded.set(false); }

    static ComposeController of(Node compose) {
        return (ComposeController)compose.getProperties().get(ComposeController.class);
    }
}
