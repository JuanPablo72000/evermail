package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.repository.AvatarRepository;
import java.time.*;
import java.util.*;

/** Used only by the avatar worker; session transitions drain that worker before deleting keys. */
public final class AvatarService {
    private record Cached(AvatarPhoto photo, Instant expires) { }
    private final AuthService auth;
    private final AvatarRepository repository;
    private final GooglePeopleClient people;
    private final Clock clock;
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private UUID accountId;
    public AvatarService(AuthService auth, AvatarRepository repository) {
        this(auth, repository, new GooglePeopleClient(), Clock.systemUTC());
    }
    AvatarService(AuthService auth, AvatarRepository repository, GooglePeopleClient people, Clock clock) {
        this.auth = auth; this.repository = repository; this.people = people; this.clock = clock;
    }
    public void reset() { cache.clear(); people.reset(); accountId = null; }

    public AvatarPhoto load(Account account, String sender, boolean online, Deadline deadline) throws Exception {
        deadline.check();
        if (!account.getId().equals(accountId)) { reset(); accountId = account.getId(); }
        if (account.getProvider() != OAuthProvider.GOOGLE) return AvatarPhoto.empty(AvatarPhoto.Status.ABSENT);
        String address = AvatarPhoto.address(sender);
        if (!address.contains("@") || address.length() > 320) return AvatarPhoto.empty(AvatarPhoto.Status.ABSENT);
        Cached known = cache.get(address);
        if (known != null && clock.instant().isBefore(known.expires)) return known.photo;
        AvatarPhoto photo;
        try { photo = repository.read(accountId, address, clock.instant()); }
        catch (Exception unreadable) { photo = null; }
        if (photo != null) return remember(address, photo);
        if (!online) return AvatarPhoto.empty(AvatarPhoto.Status.UNAVAILABLE);
        try (var reservation = repository.reserve(address)) {
            String token = auth.ensureCredentials(accountId, deadline).getAccessToken();
            photo = people.lookup(token, address, deadline);
            deadline.check();
            if (photo.status() == AvatarPhoto.Status.AVAILABLE || photo.status() == AvatarPhoto.Status.ABSENT) {
                try { repository.save(accountId, address, photo, clock.instant(), reservation); }
                catch (Exception unavailable) { deadline.check(); /* Optional disk cache must not hide a valid photo. */ }
            }
            return remember(address, photo);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw interrupted;
        } catch (Exception unavailable) {
            deadline.check();
            return remember(address, AvatarPhoto.empty(AvatarPhoto.Status.UNAVAILABLE));
        }
    }

    private AvatarPhoto remember(String address, AvatarPhoto photo) {
        long seconds = switch (photo.status()) {
            case AVAILABLE -> 86400;
            case ABSENT, PERMISSION_REQUIRED -> 600;
            case UNAVAILABLE -> 60;
        };
        cache.put(address, new Cached(photo, clock.instant().plusSeconds(seconds)));
        while (cache.size() > 128) cache.pollFirstEntry();
        return photo;
    }
}
