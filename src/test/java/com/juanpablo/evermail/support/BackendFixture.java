package com.juanpablo.evermail.support;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.*;
import com.juanpablo.evermail.service.*;
import com.juanpablo.evermail.util.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BackendFixture {
    @TempDir
    protected Path directory;
    protected TransactionManager transactions;
    protected AccountRepository accounts;
    protected MailRepository mails;
    protected OutboxRepository outbox;
    protected MemoryKeys keys;
    protected SecurityUtil security;
    protected Account account;
    protected AccountCoordinator coordinator;
    protected FakeGateway gateway;
    protected MailSendService sender;

    @BeforeEach
    void initializeBackend() throws Exception {
        security = new SecurityUtil();
        keys = new MemoryKeys(security);
        transactions = new TransactionManager(new SqliteConnectionProvider(directory.resolve("test.db")));
        new DatabaseMigrator(transactions).migrate();
        accounts = new AccountRepository(transactions, security, keys);
        mails = new MailRepository(transactions, accounts, security, keys);
        outbox = new OutboxRepository(transactions, accounts, keys, security);
        account = createAccount("first@example.com");
        coordinator = new AccountCoordinator();
        gateway = new FakeGateway();
        sender = new MailSendService(gateway, outbox, new ComposeService(), coordinator);
    }

    @AfterEach
    void closeBackend() throws Exception {
        if (transactions != null) {
            transactions.close();
        }
    }

    protected Account createAccount(String email) throws Exception {
        Account created = accounts.beginProvisioning(new Identity(OAuthProvider.GOOGLE, "issuer|" + email, email, "Person"));
        keys.create(created.getKeyRef());
        accounts.activate(created, new OAuthCredentials("access", "refresh", Instant.now().plusSeconds(3600)));
        return accounts.find(created.getId());
    }

    protected Recipient recipient(String email, RecipientType type) {
        return new Recipient(email, email, null, type);
    }

    protected ComposeRequest request(UUID id) {
        return new ComposeRequest(id, account.getId(), "Subject", "Private body",
                List.of(recipient("target@example.com", RecipientType.TO)));
    }

    protected Deadline budget() {
        return Deadline.after(Duration.ofSeconds(10));
    }

    protected long count(String table) throws Exception {
        return transactions.read(c -> Sql.one(c, "SELECT COUNT(*) FROM " + table, rs -> rs.getLong(1)));
    }

    protected RemoteInboxPage page(long validity, long from, long to, boolean more) {
        List<RemoteMessage> messages = new ArrayList<>();
        for (long uid = to; uid >= from; uid--) {
            messages.add(new RemoteMessage(new RemoteMailHeader(new RemoteMailId(validity, uid),
                    "<" + uid + "@example.com>", "sender@example.com", "Sender", "Mail " + uid,
                    Instant.parse("2026-09-20T12:00:00Z").plusSeconds(uid)),
                    List.of(recipient(account.getEmail(), RecipientType.TO))));
        }
        return new RemoteInboxPage(validity, from, to, messages, more);
    }

    public static class MemoryKeys implements KeyStoreService {
        public final Map<String, SecretKey> values = new ConcurrentHashMap<>();
        private final SecurityUtil security;

        public MemoryKeys(SecurityUtil security) {
            this.security = security;
        }

        @Override
        public void create(String ref) throws CryptoException {
            if (values.putIfAbsent(ref, security.generateKey()) != null) {
                throw new CryptoException(ErrorCode.CRYPTO_OPERATION_FAILED, "Existing key");
            }
        }

        @Override
        public SecretKey read(String ref) throws CryptoException {
            if (!values.containsKey(ref)) {
                throw new CryptoException(ErrorCode.KEY_NOT_FOUND, "Missing key");
            }
            return values.get(ref);
        }

        @Override
        public void delete(String ref) {
            values.remove(ref);
        }
    }

    public static class FakeGateway implements MailGateway {
        public final AtomicInteger submissions = new AtomicInteger();
        public SmtpSession.Outcome outcome = SmtpSession.Outcome.ACCEPTED;
        public boolean throwDuringSend;
        public Runnable afterSubmission = () -> {};
        public RemoteInboxPage remotePage;
        public AtomicInteger inboxRequests = new AtomicInteger();

        @Override
        public SmtpSession openSmtp(UUID id, Deadline deadline) {
            return new SmtpSession() {
                @Override
                public Outcome submit(OutboxMessage message, Deadline ignored) throws EvermailException {
                    submissions.incrementAndGet();
                    afterSubmission.run();
                    if (throwDuringSend) {
                        throw new MailSendException(ErrorCode.NETWORK_TIMEOUT, "Response lost");
                    }
                    return outcome;
                }
                @Override
                public void close() {
                }
            };
        }

        @Override
        public InboxSession openInbox(UUID id, Deadline deadline) {
            inboxRequests.incrementAndGet();
            return new InboxSession() {
                @Override
                public RemoteInboxPage fetchLatest(int size, Deadline ignored) {
                    return remotePage;
                }
                @Override
                public RemoteInboxPage fetchBefore(InboxCursor cursor, int size, Deadline ignored) {
                    return remotePage;
                }
                @Override
                public RemoteMailContent fetchContent(RemoteMailId remote, Deadline ignored) {
                    return new RemoteMailContent("Downloaded", List.of());
                }
                @Override
                public void close() {
                }
            };
        }
    }
}
