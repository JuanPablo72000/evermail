package com.juanpablo.evermail.diagnostics;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.config.EnvConfig;
import com.juanpablo.evermail.config.StorageConfig;
import com.juanpablo.evermail.model.AvatarPhoto;
import com.juanpablo.evermail.model.CryptoContext;
import com.juanpablo.evermail.model.OAuthCredentials;
import com.juanpablo.evermail.model.OAuthProvider;
import com.juanpablo.evermail.service.GooglePeopleClient;
import com.juanpablo.evermail.service.MailSessionProvider;
import com.juanpablo.evermail.service.OAuthClient;
import com.juanpablo.evermail.service.OsKeyStoreService;
import com.juanpablo.evermail.util.SecurityUtil;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Read-only database access and People GETs. Never logs identities, credentials or provider payloads. */
public final class AvatarDiagnostics {
    private AvatarDiagnostics() {
    }

    public static void main(String[] args) {
        try {
            run(Arrays.asList(args).contains("--inbox"));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.out.println("AVATAR_DIAGNOSTIC_FAILED=InterruptedException");
        } catch (Exception failure) {
            System.out.println("AVATAR_DIAGNOSTIC_FAILED=" + failure.getClass().getSimpleName());
        }
    }

    private static void run(boolean inspectInbox) throws Exception {
        var path = StorageConfig.databasePath();
        if (!Files.isRegularFile(path)) {
            System.out.println("NO_SAVED_DATABASE");
            return;
        }
        var security = new SecurityUtil();
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        OAuthCredentials credentials;
        String accountEmail;
        try (var database = DriverManager.getConnection("jdbc:sqlite:" + path, config.toProperties());
             var keys = new OsKeyStoreService(security);
             var statement = database.prepareStatement("""
                     SELECT id_account,key_ref,access_token_cipher,refresh_token_cipher,token_expires_at,email
                     FROM account WHERE provider='GOOGLE' AND status='ACTIVE' LIMIT 1
                     """);
             var row = statement.executeQuery()) {
            if (!row.next()) {
                System.out.println("NO_ACTIVE_GOOGLE_ACCOUNT");
                return;
            }
            UUID id = UUID.fromString(row.getString(1));
            var key = keys.read(row.getString(2));
            credentials = new OAuthCredentials(
                    security.decrypt(row.getString(3), key, new CryptoContext(id, id, "access")),
                    security.decrypt(row.getString(4), key, new CryptoContext(id, id, "refresh")),
                    Instant.ofEpochMilli(row.getLong(5)));
            accountEmail = row.getString(6);
        }
        // Release SQLite's read lock before network I/O so diagnostics do not stall the running app.
        if (credentials.getExpiresAt().isBefore(Instant.now().plusSeconds(30))) {
            credentials = new OAuthClient().refresh(new EnvConfig().loadProvider(OAuthProvider.GOOGLE), credentials,
                    Deadline.after(Duration.ofSeconds(20)));
            // Use the renewed token only in memory; the running app owns credential persistence.
        }
        var client = new GooglePeopleClient();
        for (var report : client.diagnose(credentials.getAccessToken(), Deadline.after(Duration.ofSeconds(45)))) {
            System.out.println(report);
        }
        System.out.println("PHOTO_SAMPLE=" + client.diagnoseSample(
                credentials.getAccessToken(), Deadline.after(Duration.ofSeconds(15))));
        if (inspectInbox) {
            inspectInbox(accountEmail, credentials.getAccessToken(), client);
        }
    }

    private static void inspectInbox(String accountEmail, String token, GooglePeopleClient client) throws Exception {
        // Fetch ENVELOPE only in READ_ONLY/PEEK mode. No bodies, marks, sends or local persistence.
        try (var store = Session.getInstance(MailSessionProvider.imapProperties(8000)).getStore("imaps")) {
            store.connect(OAuthProvider.GOOGLE.getImapHost(), OAuthProvider.GOOGLE.getImapPort(), accountEmail, token);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                int count = inbox.getMessageCount();
                if (count == 0) {
                    System.out.println("INBOX_SAMPLE=0");
                    return;
                }
                Message[] messages = inbox.getMessages(Math.max(1, count - 49), count);
                FetchProfile profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                inbox.fetch(messages, profile);
                Map<String, AvatarPhoto.Status> senders = new HashMap<>();
                Map<AvatarPhoto.Status, Integer> totals = new EnumMap<>(AvatarPhoto.Status.class);
                var deadline = Deadline.after(Duration.ofSeconds(30));
                for (Message message : messages) {
                    deadline.check();
                    Address[] from = message.getFrom();
                    if (from == null || from.length == 0 || !(from[0] instanceof InternetAddress address)) {
                        continue;
                    }
                    String email = AvatarPhoto.address(address.getAddress());
                    if (email.isEmpty()) {
                        continue;
                    }
                    if (!senders.containsKey(email)) {
                        senders.put(email, client.lookup(token, email, deadline).status());
                    }
                    totals.merge(senders.get(email), 1, Integer::sum);
                }
                System.out.println("INBOX_SAMPLE=" + messages.length + " UNIQUE_SENDERS=" + senders.size()
                        + " PHOTO_RESULTS=" + totals);
            } finally {
                inbox.close(false);
            }
        }
    }
}
