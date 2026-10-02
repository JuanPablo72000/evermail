package com.juanpablo.evermail.application;

import com.juanpablo.evermail.model.Account;

/** Immutable application state, with no screen or JavaFX dependency. */
public record SessionSnapshot(Phase phase, Account account, long version) {
    public enum Phase { NEW, STARTING, LOGIN_REQUIRED, AUTHENTICATING, READY, OFFLINE,
        REAUTH_REQUIRED, LOGGING_OUT, FAILED, CLOSED }

    public boolean hasSession() {
        return account != null && (phase == Phase.READY || phase == Phase.OFFLINE || phase == Phase.REAUTH_REQUIRED);
    }
}
