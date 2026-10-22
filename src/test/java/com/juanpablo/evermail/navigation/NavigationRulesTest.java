package com.juanpablo.evermail.navigation;

import com.juanpablo.evermail.application.SessionSnapshot;
import com.juanpablo.evermail.model.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static com.juanpablo.evermail.navigation.NavigationRules.Route.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationRulesTest {
    @Test void guardsPrivateRoutesWithoutAnAccount() {
        for (var phase : new SessionSnapshot.Phase[]{SessionSnapshot.Phase.LOGIN_REQUIRED, SessionSnapshot.Phase.AUTHENTICATING,
                SessionSnapshot.Phase.READY, SessionSnapshot.Phase.OFFLINE, SessionSnapshot.Phase.REAUTH_REQUIRED})
            assertEquals(LOGIN, NavigationRules.resolve(new SessionSnapshot(phase, null, 1), READER));
    }
    @Test void offlineAndReauthorizationPermitLocalReader() {
        Account account = new Account(UUID.randomUUID(), OAuthProvider.GOOGLE, "s", "a@example.com", "A", "key", AccountStatus.REAUTH_REQUIRED);
        for (var phase : new SessionSnapshot.Phase[]{SessionSnapshot.Phase.OFFLINE, SessionSnapshot.Phase.REAUTH_REQUIRED})
            assertEquals(READER, NavigationRules.resolve(new SessionSnapshot(phase, account, 1), READER));
    }
    @Test void lifecycleStatesAlwaysOverrideTheRequestedScreen() {
        assertEquals(LOADING, NavigationRules.resolve(new SessionSnapshot(SessionSnapshot.Phase.STARTING, null, 1), INBOX));
        assertEquals(LOADING, NavigationRules.resolve(new SessionSnapshot(SessionSnapshot.Phase.LOGGING_OUT, null, 1), READER));
        assertEquals(RECOVERY, NavigationRules.resolve(new SessionSnapshot(SessionSnapshot.Phase.FAILED, null, 1), INBOX));
        assertEquals(CLOSED, NavigationRules.resolve(new SessionSnapshot(SessionSnapshot.Phase.CLOSED, null, 1), INBOX));
    }
}
