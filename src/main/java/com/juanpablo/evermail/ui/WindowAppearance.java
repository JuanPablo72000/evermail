package com.juanpablo.evermail.ui;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import java.util.Objects;

/** Keeps native window controls; unsupported DWM colors fall back to the OS theme. */
public final class WindowAppearance {
    private WindowAppearance() {}

    public static void install(Stage stage) {
        String logo = Objects.requireNonNull(WindowAppearance.class.getResource(
                "/com/juanpablo/evermail/images/Logo_Evermail.png")).toExternalForm();
        stage.getIcons().setAll(new Image(logo, 32, 32, true, true),
                new Image(logo, 256, 256, true, true));
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> applyCaption(stage));
    }

    private static void applyCaption(Stage stage) {
        if (!Platform.isWindows() || stage.getStyle() == StageStyle.TRANSPARENT
                || stage.getStyle() == StageStyle.UNDECORATED) return;
        try {
            HWND hwnd = findWindow(stage.getTitle());
            if (hwnd == null) return;
            Dwm dwm = Native.load("dwmapi", Dwm.class);
            // COLORREF is 0x00BBGGRR; #251a30 matches Common.css.
            dwm.DwmSetWindowAttribute(hwnd, 20, new IntByReference(1), Integer.BYTES);
            dwm.DwmSetWindowAttribute(hwnd, 35, new IntByReference(0x00301a25), Integer.BYTES);
            dwm.DwmSetWindowAttribute(hwnd, 36, new IntByReference(0x00ffffff), Integer.BYTES);
        } catch (LinkageError | RuntimeException unavailable) {
            // Cosmetic integration must never prevent opening or closing the application.
        }
    }

    static HWND findWindow(String title) {
        HWND[] found = {null};
        long pid = ProcessHandle.current().pid();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            IntByReference owner = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner);
            if (Integer.toUnsignedLong(owner.getValue()) != pid) return true;
            char[] text = new char[1024];
            User32.INSTANCE.GetWindowText(hwnd, text, text.length);
            if (!title.equals(Native.toString(text))) return true;
            found[0] = hwnd;
            return false;
        }, null);
        return found[0];
    }

    public interface Dwm extends StdCallLibrary {
        int DwmSetWindowAttribute(HWND hwnd, int attribute, IntByReference value, int size);
        int DwmGetWindowAttribute(HWND hwnd, int attribute, IntByReference value, int size);
    }
}
