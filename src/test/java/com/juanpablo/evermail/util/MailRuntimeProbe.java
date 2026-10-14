package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.Deadline;
import java.time.Duration;

/** Launched as the real application module, without JUnit's additional module roots. */
public final class MailRuntimeProbe {
    public static void main(String[] args) throws Exception {
        if (!MailRuntimeProbe.class.getModule().isNamed()) throw new AssertionError("Expected modular launch");
        var body = HtmlMail.sanitize("<style>p{color:#123456}</style><p>Runtime check</p>",
                cid -> null, Deadline.after(Duration.ofSeconds(10)));
        if (!body.html().contains("color:#123456") || !body.html().contains("Runtime check"))
            throw new AssertionError("HTML/CSS runtime failed");
        System.out.println("Modular HTML/CSS runtime OK");
        if (com.sun.jna.Platform.isWindows()) {
            com.sun.jna.Native.load("dwmapi", com.juanpablo.evermail.ui.WindowAppearance.Dwm.class);
            System.out.println("Modular native window appearance runtime OK");
        }
    }
}
