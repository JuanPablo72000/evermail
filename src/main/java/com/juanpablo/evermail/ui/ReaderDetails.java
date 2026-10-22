package com.juanpablo.evermail.ui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/** Sender/date and recipient/format rows stack when the reader gets narrow. */
public final class ReaderDetails extends GridPane {
    private Boolean previousStacked;
    private int previousChildCount = -1;
    public ReaderDetails() {
        widthProperty().addListener((o, before, after) -> arrange());
        getChildren().addListener((ListChangeListener<Node>) change -> arrange());
    }
    private void arrange() {
        boolean stacked = getWidth() < 650;
        if (previousStacked != null && previousStacked == stacked
                && previousChildCount == getChildren().size()) return;
        previousStacked = stacked;
        previousChildCount = getChildren().size();
        getColumnConstraints().clear();
        for (int i = 0; i < (stacked ? 1 : 2); i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setMinWidth(0); column.setHgrow(Priority.ALWAYS);
            column.setPercentWidth(stacked ? 100 : i == 0 ? 62 : 38);
            getColumnConstraints().add(column);
        }
        for (int i = 0; i < getChildren().size(); i++) {
            Node child = getChildren().get(i);
            setColumnIndex(child, stacked ? 0 : i % 2);
            setRowIndex(child, stacked ? i : i / 2);
        }
    }
}
