package com.juanpablo.evermail.ui;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class WindowAppearanceTest {
    @Test void nativeWindowHasBrandIconAndCaptionWithoutReplacingDecorations() throws Exception {
        assumeTrue(com.sun.jna.Platform.isWindows());
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        FutureTask<Void> check = new FutureTask<>(() -> {
            Stage stage = new Stage();
            try {
                stage.setTitle("Evermail appearance verification");
                stage.setScene(new Scene(new StackPane(), 360, 220));
                WindowAppearance.install(stage);
                stage.show();
                assertEquals(javafx.stage.StageStyle.DECORATED, stage.getStyle());
                assertFalse(stage.getIcons().getFirst().isError());
                var hwnd = WindowAppearance.findWindow(stage.getTitle());
                assertNotNull(hwnd);
                var dwm = Native.load("dwmapi", WindowAppearance.Dwm.class);
                var color = new IntByReference();
                int result = dwm.DwmGetWindowAttribute(hwnd, 35, color, Integer.BYTES);
                // Older Windows versions do not implement custom caption colors.
                if (result == 0) {
                    assertEquals(0x00301a25, color.getValue());
                    assertEquals(0, dwm.DwmGetWindowAttribute(hwnd, 36, color, Integer.BYTES));
                    assertEquals(0x00ffffff, color.getValue());
                }
            } finally { stage.close(); }
            return null;
        });
        Platform.runLater(check);
        check.get(20, TimeUnit.SECONDS);
    }
}
