package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.util.HtmlMail;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailHtmlLayoutTest {
    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<T>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }

    @Test void stylesheetCascadeAndColumnsReflowWithoutExecutingOrLoadingSenderResources() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        var calls = new AtomicInteger();
        var trap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        trap.createContext("/", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        trap.start();
        String origin = "http://127.0.0.1:" + trap.getAddress().getPort();
        String raw = """
                <style>
                @import url('%s/sheet');
                @font-face{font-family:tracker;src:url('%s/font')}
                body{margin:0;background-color:#f2f4f8;font-family:Arial,sans-serif}
                .card{width:640px;background-color:#ffffff;border-collapse:collapse}
                .header{background-color:#183653;color:#ffffff;padding:32px}
                .column{width:50%%;padding:24px;vertical-align:top;box-sizing:border-box}
                h1{font-size:28px;margin:0;color:#ffffff}
                .label{color:#667788;font-size:12px;letter-spacing:2px}
                .title{color:#183653;font-size:22px;line-height:1.2}
                #highlight{color:rgb(10,100,90)}
                @media screen and (max-width:600px){
                    .card{width:100%%!important}
                    .column{display:block!important;width:100%%!important}
                }
                </style>
                <div id="preheader" style="max-height:0;max-width:0;opacity:0;overflow:hidden">
                Mira quién se ha puesto en contacto contigo. Tienes nuevas actualizaciones.
                </div>
                <div hidden>Hidden preview padding</div>
                <table class="card" align="center" cellpadding="0" cellspacing="0">
                <tr><td colspan="2" class="header"><h1>Novedades de octubre</h1><p>Un correo que conserva su diseño.</p></td></tr>
                <tr><td id="first" class="column"><p class="label">DISEÑO</p><h2 class="title">Tablas y estilos</h2>
                <p id="highlight" style="color:#2050a0">Columnas, colores y espacios del mensaje original.</p></td>
                <td id="second" class="column"><p class="label">LECTURA</p><h2 class="title">Se adapta a tu pantalla</h2>
                <p>Las columnas se apilan cuando el lector es estrecho.</p></td></tr></table>
                <div style="background-image:url('%s/background')"></div>
                <script>fetch('%s/script')</script>
                """.formatted(origin, origin, origin, origin);
        String cached = HtmlMail.sanitize(raw, cid -> null, null, true, Deadline.after(Duration.ofSeconds(5))).html();
        String safe = HtmlMail.sanitize(cached, cid -> null, Deadline.after(Duration.ofSeconds(5))).html();
        WebView web = fx(() -> new WebView());
        javafx.stage.Stage stage = fx(javafx.stage.Stage::new);
        try {
            for (int width : new int[]{900, 360, 900}) {
                var loaded = new CompletableFuture<Void>();
                fx(() -> {
                    web.getEngine().setJavaScriptEnabled(false);
                    web.getEngine().getLoadWorker().stateProperty().addListener((o, old, state) -> {
                        if (state == Worker.State.SUCCEEDED) loaded.complete(null);
                        if (state == Worker.State.FAILED) loaded.completeExceptionally(new AssertionError("HTML failed to load"));
                    });
                    StackPane root = new StackPane(web); stage.setScene(new Scene(root, width, 650));
                    stage.sizeToScene(); stage.show();
                    root.resize(width, 650); root.applyCss(); root.layout();
                    web.getEngine().loadContent(HtmlMail.document(safe));
                    return null;
                });
                loaded.get(10, TimeUnit.SECONDS);
                fx(() -> {
                    var engine = web.getEngine();
                    assertFalse(engine.isJavaScriptEnabled());
                    // Only the test invokes executeScript to inspect layout; sender scripts remain disabled.
                    assertEquals("rgb(32, 80, 160)", engine.executeScript("getComputedStyle(document.getElementById('highlight')).color"));
                    String display = (String)engine.executeScript("getComputedStyle(document.getElementById('first')).display");
                    assertEquals(width < 600 ? "block" : "table-cell", display);
                    double overflow = ((Number)engine.executeScript("document.documentElement.scrollWidth-window.innerWidth")).doubleValue();
                    assertTrue(overflow <= 1, "Horizontal overflow: " + overflow);
                    assertEquals("hidden", engine.executeScript("getComputedStyle(document.getElementById('preheader')).overflow"));
                    assertEquals("0", engine.executeScript("getComputedStyle(document.getElementById('preheader')).opacity"));
                    double preheaderHeight = ((Number)engine.executeScript("document.getElementById('preheader').getBoundingClientRect().height")).doubleValue();
                    assertEquals(0, preheaderHeight, 0.1);
                    double extraHeight = ((Number)engine.executeScript("document.documentElement.scrollHeight-Math.max(window.innerHeight,document.querySelector('.card').getBoundingClientRect().bottom)")).doubleValue();
                    assertTrue(extraHeight <= 1, "Preheader must not create a blank scroll area: " + extraHeight);
                    assertEquals(0, engine.getDocument().getElementsByTagName("script").getLength());
                    FxmlViewsTest.save(web.snapshot(null, null), "mail-html-" + width + ".png");
                    return null;
                });
            }
            assertEquals(0, calls.get());
        } finally { fx(() -> { web.getEngine().loadContent(""); stage.close(); return null; }); trap.stop(0); }
    }
}
