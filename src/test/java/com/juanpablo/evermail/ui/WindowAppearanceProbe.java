package com.juanpablo.evermail.ui;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Exercises callbacks as a named module, which JUnit's classpath cannot verify. */
public final class WindowAppearanceProbe {
    public static void verify() throws Exception {
        var ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timed out");
        var create = new FutureTask<Stage>(() -> {
            Stage stage = new Stage();
            try {
                stage.setTitle("Evermail modular appearance verification");
                stage.setScene(new Scene(new StackPane(), 360, 220));
                WindowAppearance.install(stage);
                stage.show();
                return stage;
            } catch (Throwable failure) { stage.close(); throw failure; }
        });
        try {
            Platform.runLater(create);
            Stage stage = create.get(20, TimeUnit.SECONDS);
            var check = new FutureTask<Void>(() -> {
                try {
                    var hwnd = WindowAppearance.findWindow(stage.getTitle());
                    if (hwnd == null) throw new AssertionError("Native callback did not find the stage");
                    var dwm = Native.load("dwmapi", WindowAppearance.Dwm.class);
                    for (int[] attribute : new int[][]{{35, 0x00301a25}, {36, 0x00ffffff}, {34, 0x00e93420}, {33, 2}}) {
                        var value = new IntByReference();
                        int result = dwm.DwmGetWindowAttribute(hwnd, attribute[0], value, Integer.BYTES);
                        if (result == 0 && value.getValue() != attribute[1])
                            throw new AssertionError("Native appearance not applied: " + attribute[0] + " = " + value.getValue());
                    }
                } finally { stage.close(); }
                return null;
            });
            Platform.runLater(check); check.get(20, TimeUnit.SECONDS);
        } finally { Platform.exit(); }
    }
}
