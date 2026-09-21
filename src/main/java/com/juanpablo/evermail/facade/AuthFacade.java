package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.AuthService;
import com.juanpablo.evermail.config.*;
import javafx.concurrent.Task;
import java.util.UUID;

/** Backend task adapter. Creating a task never starts it or changes a screen. */
public class AuthFacade {
    private final AuthService auth;

    public AuthFacade(AuthService auth) {
        this.auth = auth;
    }

    public Task<Account> loginTask(OAuthProvider provider) {
        CancellationToken cancellation = new CancellationToken();
        return new Task<>() {
            @Override
            protected Account call() throws Exception {
                return auth.login(provider, cancellation);
            }

            @Override
            protected void cancelled() {
                cancellation.cancel();
            }
        };
    }

    public Task<Void> logoutTask(UUID accountId) {
        
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                auth.logout(accountId); return null;
            }
        };
    }
}
