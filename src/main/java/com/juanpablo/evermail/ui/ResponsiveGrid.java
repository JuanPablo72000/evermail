package com.juanpablo.evermail.ui;

import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.layout.*;

/** Grid constraints, never absolute coordinates. Narrow windows keep all panels accessible by scrolling. */
public final class ResponsiveGrid extends GridPane {
    private String mode = "main";
    private int configuration = -1;

    public ResponsiveGrid() {
        setMinWidth(0);
        widthProperty().addListener((o, a, b) -> arrange());
        getChildren().addListener((ListChangeListener<Node>) c -> { configuration = -1; arrange(); });
    }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; configuration = -1; arrange(); }

    private void arrange() {
        double width = getWidth();
        int next = mode.equals("main") ? width >= 1100 ? 3 : width >= 760 ? 2 : 1
                : width >= (mode.equals("login") ? 680 : 760) ? 2 : 1;
        if (!mode.equals("login") && !getChildren().isEmpty()) {
            getChildren().getFirst().pseudoClassStateChanged(PseudoClass.getPseudoClass("narrow"),
                    (next == 1 ? width : width * .26) < 310);
        }
        if (configuration == next) return;
        configuration = next;
        getColumnConstraints().clear(); getRowConstraints().clear();
        double[] ratios = next == 1 ? new double[]{100}
                : mode.equals("login") ? new double[]{60, 40}
                : next == 3 ? new double[]{26, 44, 30} : new double[]{26, 74};
        for (double ratio : ratios) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(ratio); column.setMinWidth(0); column.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(column);
        }
        for (int i = 0; i < getChildren().size(); i++) {
            Node child = getChildren().get(i);
            clearConstraints(child);
            setHgrow(child, Priority.ALWAYS); setVgrow(child, Priority.ALWAYS);
            setFillWidth(child, true); setFillHeight(child, true);
            if (next == 1) { setRowIndex(child, i); setColumnIndex(child, 0); }
            else if (mode.equals("main") && next == 2 && i == 2) {
                setRowIndex(child, 1); setColumnIndex(child, 0); setColumnSpan(child, 2);
            } else { setRowIndex(child, 0); setColumnIndex(child, i); }
            child.pseudoClassStateChanged(PseudoClass.getPseudoClass("compact"), next == 1);
        }
        pseudoClassStateChanged(PseudoClass.getPseudoClass("compact"), next == 1);
        requestLayout();
    }

    @Override protected double computeMinHeight(double width) {
        // A ScrollPane can grow vertically rather than clipping forms at a small viewport height.
        return super.computePrefHeight(width);
    }
}
