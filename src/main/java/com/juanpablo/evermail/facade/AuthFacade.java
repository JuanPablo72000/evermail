package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.OAuthProvider;
import com.juanpablo.evermail.service.AuthService;
import javafx.concurrent.Task;

/**
 * Exposure layer for authentication. Only builds and returns Tasks; the
 * controller decides when to run them.
 */
public class AuthFacade {

    private final AuthService authService;

    public AuthFacade(AuthService authService) {
        this.authService = authService;
    }

    public Task<Account> loginTask(OAuthProvider provider) {
        return new Task<>() {
            @Override
            protected Account call() throws Exception {
                return authService.login(provider);
            }
        };
    }

    public Task<Void> logoutTask(Account account) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                authService.logout(account);
                return null;
            }
        };
    }
}