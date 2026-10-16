package com.juanpablo.evermail.ui;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.presentation.MailViewState.BodyMode;
import com.juanpablo.evermail.service.MailPresentationService;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.Test;
import org.w3c.dom.events.*;
import static org.junit.jupiter.api.Assertions.*;

class SafeMailBodyTest {
    @Test void secondStageReplacesPlaceholderWithDecodedImageInWebView() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, 1), "png", bytes);
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        UUID id = UUID.randomUUID();
        var firstContent = new MailContent(id, "Readable", "<p>Readable</p><img src='https://origin.invalid/p'>", true, false, List.of());
        var finalContent = new MailContent(id, "Readable", "<p>Readable</p><img src='" + data + "'>", false, false, List.of());
        var service = new MailPresentationService();
        var first = service.prepareWithoutImages(firstContent, Deadline.after(Duration.ofSeconds(5)));
        var complete = service.prepareWithoutImages(finalContent, Deadline.after(Duration.ofSeconds(5)));
        var rendered = new java.util.concurrent.LinkedBlockingQueue<WebView>();
        SafeMailBody body = fx(() -> {
            var view = new SafeMailBody(uri -> {}, message -> {});
            view.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> {
                if (view.getChildren().getFirst() instanceof WebView web) rendered.add(web);
            });
            view.show(first, BodyMode.HTML);
            return view;
        });
        try {
            WebView initial = rendered.poll(5, TimeUnit.SECONDS);
            assertNotNull(initial);
            fx(() -> { assertEquals(0, initial.getEngine().getDocument().getElementsByTagName("img").getLength()); return null; });
            var loaded = new CompletableFuture<Void>();
            fx(() -> {
                var document = initial.getEngine().getDocument();
                body.show(complete, BodyMode.HTML);
                assertSame(document, initial.getEngine().getDocument(), "Image batches must preserve the document");
                var image = document.getElementsByTagName("img").item(0);
                ((EventTarget) image).addEventListener("load", event -> loaded.complete(null), false);
                if (((Number) initial.getEngine().executeScript("document.images[0].naturalWidth")).intValue() == 2)
                    loaded.complete(null);
                return null;
            });
            loaded.get(5, TimeUnit.SECONDS);
            WebView enhanced = initial;
            fx(() -> {
                assertFalse(enhanced.getEngine().isJavaScriptEnabled());
                assertEquals(1, enhanced.getEngine().getDocument().getElementsByTagName("img").getLength());
                // Trusted test-only inspection; sender JavaScript remains disabled.
                assertEquals(2, ((Number) enhanced.getEngine().executeScript("document.images[0].naturalWidth")).intValue());
                return null;
            });
        } finally { fx(() -> { body.clear(); return null; }); }
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(10,TimeUnit.SECONDS);
    }
    @Test void sanitizedHtmlDoesNotLoadTrackersOrScriptsAndClearRemovesPlaintext() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(10,TimeUnit.SECONDS));
        AtomicInteger requests = new AtomicInteger();
        HttpServer trap = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        trap.createContext("/", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(204,-1); exchange.close(); }); trap.start();
        AtomicReference<SafeMailBody> body = new AtomicReference<>();
        try {
            String remote = "http://127.0.0.1:"+trap.getAddress().getPort()+"/track";
            MailContent content = new MailContent(UUID.randomUUID(),"PRIVATE_BODY","<h1>Message</h1><script>location='"+remote+"'</script><img src='"+remote+"'><a href='https://example.com'>Link</a>",true,false,List.of());
            MailPresentation prepared = new MailPresentationService().prepare(content,Deadline.after(Duration.ofSeconds(2)));
            CompletableFuture<WebView> rendered = new CompletableFuture<>(); AtomicReference<URI> link = new AtomicReference<>();
            fx(() -> {
                SafeMailBody view = new SafeMailBody(link::set,message -> {}); body.set(view);
                view.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> {
                    if (view.getChildren().getFirst() instanceof WebView web) rendered.complete(web);
                }); view.show(prepared,BodyMode.HTML); return null;
            });
            WebView web = rendered.get(4,TimeUnit.SECONDS);
            fx(() -> {
                assertFalse(web.getEngine().isJavaScriptEnabled());
                var doc = web.getEngine().getDocument();
                assertEquals(0,doc.getElementsByTagName("script").getLength()); assertEquals(0,doc.getElementsByTagName("img").getLength());
                Event click = ((DocumentEvent)doc).createEvent("Event"); click.initEvent("click",true,true);
                ((EventTarget)doc.getElementsByTagName("a").item(0)).dispatchEvent(click);
                assertEquals(URI.create("https://example.com"),link.get());
                body.get().show(prepared,BodyMode.TEXT); assertEquals("PRIVATE_BODY",((TextArea)body.get().getChildren().getFirst()).getText());
                body.get().clear(); assertEquals("",((TextArea)body.get().getChildren().getFirst()).getText());
                link.set(null); ((EventTarget)doc.getElementsByTagName("a").item(0)).dispatchEvent(click); assertNull(link.get()); return null;
            });
            assertEquals(0,requests.get());
        } finally { if (body.get()!=null) fx(() -> { body.get().clear(); return null; }); trap.stop(0); }
    }
}
