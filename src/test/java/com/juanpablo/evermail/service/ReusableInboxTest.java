package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import jakarta.mail.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReusableInboxTest {
    private Deadline budget() { return Deadline.after(Duration.ofSeconds(5)); }

    @Test void oldOperationAndIdleTimersCannotCloseANewerBorrow() throws Exception {
        try (var timer = new ManualTimer()) {
            var store = new FakeStore();
            var inbox = new MailSessionProvider.ImapInbox(store, new FakeFolder(store), null, timer);
            assertTrue(inbox.borrow(budget()));
            assertFalse(inbox.borrow(budget()), "Only one owner may borrow the connection");
            Runnable oldDeadline = timer.tasks.getLast();
            inbox.close();
            Runnable oldIdle = timer.tasks.getLast();
            assertTrue(inbox.borrow(budget()));
            oldDeadline.run(); oldIdle.run(); // Model callbacks already dispatched before cancellation.
            assertEquals(0, store.closes);
            assertTrue(inbox.isLeased());
            timer.tasks.getLast().run();
            assertEquals(1, store.closes);
            inbox.close();
            assertFalse(inbox.borrow(budget()));
        }
    }

    @Test void idleExpiryAndExplicitDisposalPreventReuse() throws Exception {
        try (var timer = new ManualTimer()) {
            var store = new FakeStore();
            var inbox = new MailSessionProvider.ImapInbox(store, new FakeFolder(store), null, timer);
            assertTrue(inbox.borrow(budget()));
            inbox.close();
            assertEquals(0, store.closes);
            timer.tasks.getLast().run();
            assertFalse(inbox.borrow(budget()));
            var other = new MailSessionProvider.ImapInbox(new FakeStore(), new FakeFolder(store), null, timer);
            assertTrue(other.borrow(budget()));
            other.dispose();
            assertFalse(other.borrow(budget()));
        }
    }

    private static class ManualTimer extends ScheduledThreadPoolExecutor {
        final List<Runnable> tasks = new ArrayList<>();
        ManualTimer() { super(1); }
        @Override public ScheduledFuture<?> schedule(Runnable action, long delay, TimeUnit unit) {
            tasks.add(action);
            return super.schedule(() -> {}, 1, TimeUnit.DAYS);
        }
        @Override public void close() { shutdownNow(); }
    }

    private static class FakeStore extends Store {
        int closes;
        FakeStore() { super(Session.getInstance(new Properties()), null); }
        @Override public boolean isConnected() { return closes == 0; }
        @Override public void close() { closes++; }
        public Folder getDefaultFolder() { return null; }
        public Folder getFolder(String name) { return null; }
        public Folder getFolder(URLName name) { return null; }
    }

    private static class FakeFolder extends Folder {
        FakeFolder(Store store) { super(store); }
        public String getName() { return "INBOX"; }
        public String getFullName() { return "INBOX"; }
        public Folder getParent() { return null; }
        public boolean exists() { return true; }
        public Folder[] list(String pattern) { return new Folder[0]; }
        public char getSeparator() { return '/'; }
        public int getType() { return HOLDS_MESSAGES; }
        public boolean create(int type) { return false; }
        public boolean hasNewMessages() { return false; }
        public Folder getFolder(String name) { return null; }
        public boolean delete(boolean recurse) { return false; }
        public boolean renameTo(Folder target) { return false; }
        public void open(int mode) { }
        public void close(boolean expunge) { }
        public boolean isOpen() { return true; }
        public Flags getPermanentFlags() { return new Flags(); }
        public int getMessageCount() { return 0; }
        public Message getMessage(int number) { return null; }
        public void appendMessages(Message[] messages) { }
        public Message[] expunge() { return new Message[0]; }
    }
}
