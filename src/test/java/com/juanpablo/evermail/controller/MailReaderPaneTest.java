package com.juanpablo.evermail.controller;

import com.juanpablo.evermail.facade.MailFacade;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.InboxService;
import com.juanpablo.evermail.support.BackendFixture;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.w3c.dom.events.*;
import static org.junit.jupiter.api.Assertions.*;

class MailReaderPaneTest extends BackendFixture {
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }

    @Test void rendersOfflineWithoutRemoteRequestsAndClearsOnLogout() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer trap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        trap.createContext("/", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        trap.start();
        AtomicReference<MailReaderPane> reference = new AtomicReference<>();
        try {
            String remote = "http://127.0.0.1:" + trap.getAddress().getPort() + "/track";
            mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
            UUID id = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
            mails.saveContent(account.getId(), id, new RemoteMailContent("Fallback", "<h1>Mensaje</h1><img src='" + remote
                    + "'><script>location='" + remote + "'</script><div style='background-image:url(" + remote + ")'>Texto</div>"
                    + "<a href='https://example.com'>Enlace</a>", true, List.of()));
            CompletableFuture<WebView> visible = new CompletableFuture<>();
            AtomicReference<URI> clicked = new AtomicReference<>();
            fx(() -> {
                MailReaderPane pane = new MailReaderPane(new MailFacade(new InboxService(gateway, mails, coordinator)), () -> {}, clicked::set);
                reference.set(pane);
                pane.centerProperty().addListener((o, a, b) -> { if (b instanceof WebView web) visible.complete(web); });
                pane.open(account.getId(), id); return null;
            });
            WebView web = visible.get(4, TimeUnit.SECONDS);
            fx(() -> {
                assertFalse(web.getEngine().isJavaScriptEnabled());
                var doc = web.getEngine().getDocument();
                assertEquals(0, doc.getElementsByTagName("script").getLength());
                assertEquals(0, doc.getElementsByTagName("img").getLength());
                Event click = ((DocumentEvent) doc).createEvent("Event"); click.initEvent("click", true, true);
                ((EventTarget) doc.getElementsByTagName("a").item(0)).dispatchEvent(click);
                return null;
            });
            assertEquals(URI.create("https://example.com"), clicked.get());
            assertEquals(0, requests.get());
            assertEquals(0, gateway.inboxRequests.get());
            fx(() -> { reference.get().clear(); return null; });
            assertTrue(fx(() -> ((javafx.scene.control.TextArea) reference.get().getCenter()).getText().isEmpty()));
        } finally {
            if (reference.get() != null) fx(() -> { reference.get().close(); return null; });
            trap.stop(0);
        }
    }
}
