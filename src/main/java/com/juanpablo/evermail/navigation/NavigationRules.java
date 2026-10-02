package com.juanpablo.evermail.navigation;

import com.juanpablo.evermail.application.SessionSnapshot;

/** Routes only; FXML loading belongs to the later JavaFX integration. */
public final class NavigationRules {
    public enum Route { LOADING, LOGIN, INBOX, READER, RECOVERY, CLOSED }
    private NavigationRules() {}

    public static Route resolve(SessionSnapshot session, Route requested) {
        return switch (session.phase()) {
            case NEW, STARTING, LOGGING_OUT -> Route.LOADING;
            case LOGIN_REQUIRED, AUTHENTICATING -> Route.LOGIN;
            case FAILED -> Route.RECOVERY;
            case CLOSED -> Route.CLOSED;
            case READY, OFFLINE, REAUTH_REQUIRED -> session.hasSession()
                    ? requested == Route.READER ? Route.READER : Route.INBOX : Route.LOGIN;
        };
    }
}
