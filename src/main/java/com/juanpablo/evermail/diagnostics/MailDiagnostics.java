package com.juanpablo.evermail.diagnostics;

import com.juanpablo.evermail.application.OperationError;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.service.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

/** Checks saved sessions without sending mail or printing account identifiers, tokens or message data. */
public final class MailDiagnostics {
    public static void main(String[] args) {
        try {
            if (!Files.isRegularFile(StorageConfig.databasePath())) {
                System.out.println("NO_SAVED_DATABASE");
                return;
            }
            try (BackendContext backend = BackendContext.create()) {
                var accounts = backend.getAccounts().list();
                System.out.println("SAVED_ACCOUNTS=" + accounts.size());
                var gateway = new MailSessionProvider(backend.getAuth(), backend.getAccounts());
                for (var account : accounts) {
                    System.out.println("PROVIDER=" + account.getProvider());
                    check("TOKEN_REFRESH", () -> {
                        var config = new EnvConfig().loadProvider(account.getProvider());
                        var previous = backend.getAccounts().readCredentials(account.getId());
                        var refreshed = new OAuthClient().refresh(config, previous, deadline());
                        backend.getAccounts().activate(account, refreshed);
                    });
                    check("IMAP_READ", () -> {
                        var budget = deadline();
                        try (var inbox = gateway.openInbox(account.getId(), budget)) {
                            inbox.fetchLatest(1, budget);
                        }
                    });
                    check("SMTP_AUTH_ONLY", () -> {
                        try (var smtp = gateway.openSmtp(account.getId(), deadline())) {
                            // Authentication only. No MAIL FROM, RCPT TO or DATA commands.
                        }
                    });
                }
            }
        } catch (Exception failure) {
            report("SETUP", failure);
        }
    }

    private static Deadline deadline() { return Deadline.after(Duration.ofSeconds(30)); }
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void check(String name, Check check) {
        try { check.run(); System.out.println(name + "=OK"); }
        catch (Exception failure) { report(name, failure); }
    }
    private static void report(String name, Exception failure) {
        var error = OperationError.from(failure);
        System.out.println(name + "=FAILED code=" + error.code() + " detail=" + error.message());
        // Print only fixed allowlisted response labels, never raw server messages.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            String message = Objects.toString(cause.getMessage(), "").toUpperCase(Locale.ROOT);
            for (String marker : List.of("AUTHENTICATIONFAILED", "INVALID CREDENTIALS", "IMAP ACCESS IS DISABLED",
                    "APPLICATION-SPECIFIC PASSWORD REQUIRED", "AUTHENTICATED BUT NOT CONNECTED", "5.7.139", "5.7.3")) {
                if (message.contains(marker)) System.out.println("SERVER_MARKER=" + marker);
            }
        }
    }
}
