package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.AvatarRepository;
import com.juanpablo.evermail.support.BackendFixture;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvatarServiceTest extends BackendFixture {
    @Test void cachesMissingPhotosAndResetsDirectoryAndResultsForAnotherAccount() throws Exception {
        AtomicInteger requests = new AtomicInteger(), credentials = new AtomicInteger();
        var people = new GooglePeopleClient((uri, token, maximum, d) -> {
            requests.incrementAndGet();
            return new GooglePeopleClient.Response(200, "{}".getBytes(StandardCharsets.UTF_8));
        }, Clock.systemUTC());
        var auth = new AuthService(null, null, null, null, null, Clock.systemUTC()) {
            @Override public OAuthCredentials ensureCredentials(UUID id, Deadline d) {
                credentials.incrementAndGet();
                return new OAuthCredentials("token", "refresh", Instant.now().plusSeconds(3600));
            }
        };
        var repository = new AvatarRepository(transactions, accounts, security, keys);
        var service = new AvatarService(auth, repository, people, Clock.systemUTC());
        assertEquals(AvatarPhoto.Status.ABSENT, service.load(account, "sender@example.com", true, budget()).status());
        service.load(account, "SENDER@example.com", true, budget());
        assertEquals(2, requests.get()); assertEquals(1, credentials.get());
        service.reset();
        service.load(account, "sender@example.com", false, budget());
        assertEquals(2, requests.get(), "Encrypted cache can be read offline");
        var second = createAccount("second@example.com");
        service.load(second, "sender@example.com", true, budget());
        assertEquals(4, requests.get()); assertEquals(2, credentials.get());
    }

    @Test void cancellationReleasesReservationAndDoesNotStoreAMissingPhoto() throws Exception {
        var auth = new AuthService(null, null, null, null, null, Clock.systemUTC()) {
            @Override public OAuthCredentials ensureCredentials(UUID id, Deadline d) {
                return new OAuthCredentials("token", "refresh", Instant.now().plusSeconds(3600));
            }
        };
        var people = new GooglePeopleClient((uri, token, maximum, d) -> { throw new InterruptedException(); }, Clock.systemUTC());
        var repository = new AvatarRepository(transactions, accounts, security, keys);
        var service = new AvatarService(auth, repository, people, Clock.systemUTC());
        try {
            assertThrows(InterruptedException.class, () -> service.load(account, "sender@example.com", true, budget()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(0, count("avatar_cache"));
        try (var reservation = repository.reserve("sender@example.com")) {
            repository.save(account.getId(), "sender@example.com", AvatarPhoto.empty(AvatarPhoto.Status.ABSENT), Instant.now(), reservation);
        }
        assertEquals(1, count("avatar_cache"));
    }
}
