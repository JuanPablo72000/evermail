package com.juanpablo.evermail.diagnostics;

import com.juanpablo.evermail.application.OperationError;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.EvermailException;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.*;
import java.nio.file.Files;
import java.util.UUID;

/** Small real-network sample. Prints durations only, never message data or credentials. */
public final class ReadTimings {
    public static void main(String[] args) throws Exception {
        try { run(); }
        catch (Exception failure) {
            System.out.println("TIMING_FAILED code=" + OperationError.from(failure).code());
            // Deliberately omit the cause: provider responses can contain private data.
            throw new IllegalStateException("Read timing failed; see safe error code above");
        }
    }

    private static void run() throws Exception {
        if (!Files.isRegularFile(StorageConfig.databasePath())) {
            System.out.println("NO_SAVED_DATABASE");
            return;
        }
        int samples = 0;
        boolean failed = false;
        try (BackendContext backend = BackendContext.create()) {
            var gateway = new TimedGateway(new MailSessionProvider(backend.getAuth(), backend.getAccounts()));
            var inbox = new InboxService(gateway, backend.getMails(), new AccountCoordinator());
            var presentation = new MailPresentationService();
            for (var account : backend.getAccounts().list()) {
                // Only inspect headers already in the local first page; never erase cached bodies.
                for (var header : inbox.readCached(account.getId(), null).getItems()) {
                    if (samples >= 3) break;
                    if (header.isBodyCached() || header.getRemoteId() == null) continue;
                    samples++;
                    System.out.println("SAMPLE=" + samples);
                    try {
                        measure(inbox, presentation, account.getId(), header.getId(), "FIRST_OPEN");
                        measure(inbox, presentation, account.getId(), header.getId(), "CACHED_OPEN");
                    } catch (Exception failure) {
                        failed = true;
                        System.out.println("SAMPLE_FAILED code=" + OperationError.from(failure).code());
                    }
                }
            }
        }
        System.out.println("SAMPLES=" + samples);
        if (samples == 0) System.out.println("NO_UNCACHED_MESSAGES_IN_LOCAL_FIRST_PAGE");
        if (failed) throw new IllegalStateException("Read timing sample failed; see safe error code above");
    }

    private static void measure(InboxService inbox, MailPresentationService presentation,
                                UUID account, UUID mail, String label) throws EvermailException {
        var deadline = Deadline.after(AppConstants.CONTENT_BUDGET);
        long start = System.nanoTime();
        var content = inbox.loadContent(account, mail, deadline);
        long loaded = System.nanoTime();
        presentation.prepareWithoutImages(content, deadline);
        long readable = System.nanoTime();
        duration(label + "_READABLE_BACKEND", readable - start);
        System.out.println(label + "_READABLE_WITHIN_5S=" + (readable - start <= 5_000_000_000L));
        var prepared = presentation.prepare(content, Deadline.after(AppConstants.CONTENT_BUDGET));
        long end = System.nanoTime();
        duration(label + "_BODY", loaded - start);
        duration(label + "_INITIAL_FORMAT", readable - loaded);
        duration(label + "_FORMAT_AND_IMAGES", end - readable);
        duration(label + "_TOTAL_BACKEND", end - start);
        System.out.println(label + "_HTML=" + (prepared.getDocument() != null)
                + " BLOCKED_IMAGES=" + prepared.getContent().isBlockedRemoteImages());
        System.out.println(label + "_EMBEDDED_IMAGES=" + (prepared.getDocument() == null ? 0
                : org.jsoup.Jsoup.parse(prepared.getDocument()).select("img[src^=data:image/]").size()));
        // Do not mark read and do not render/export decrypted message content.
    }

    private static void duration(String label, long nanos) {
        System.out.println(label + "_MS=" + nanos / 1_000_000);
    }

    private record TimedGateway(MailGateway delegate) implements MailGateway {
        public InboxSession openInbox(UUID account, Deadline deadline) throws EvermailException {
            long start = System.nanoTime();
            InboxSession session = delegate.openInbox(account, deadline);
            duration("CONNECT_AUTH_AND_SELECT", System.nanoTime() - start);
            return new InboxSession() {
                public RemoteInboxPage fetchLatest(int size, Deadline d) throws EvermailException {
                    return session.fetchLatest(size, d);
                }
                public RemoteInboxPage fetchBefore(InboxCursor cursor, int size, Deadline d) throws EvermailException {
                    return session.fetchBefore(cursor, size, d);
                }
                public RemoteMailContent fetchContent(RemoteMailId id, Deadline d) throws EvermailException {
                    long start = System.nanoTime();
                    try { return session.fetchContent(id, d); }
                    finally { duration("FETCH_MIME_AND_SANITIZE", System.nanoTime() - start); }
                }
                public void close() { session.close(); }
            };
        }
        public SmtpSession openSmtp(UUID account, Deadline deadline) {
            throw new UnsupportedOperationException("Read-only diagnostic");
        }
    }
}
