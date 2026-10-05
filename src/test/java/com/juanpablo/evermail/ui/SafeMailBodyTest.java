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
