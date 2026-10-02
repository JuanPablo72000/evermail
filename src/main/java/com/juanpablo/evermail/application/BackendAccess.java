package com.juanpablo.evermail.application;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.*;
import java.util.UUID;

/** Owned backend boundary; all calls, including creation and close, run on the worker. */
public interface BackendAccess extends AutoCloseable {
    StartupResult start(Deadline deadline) throws Exception;
    Account login(OAuthProvider provider, CancellationToken cancellation) throws Exception;
    void logout(UUID accountId, Deadline deadline) throws Exception;
    InboxService inbox();
    MailSendService sender();

    static BackendAccess open() throws Exception {
        BackendContext context = BackendContext.create();
        return new BackendAccess() {
            public StartupResult start(Deadline deadline) throws Exception { return context.getStartup().start(deadline); }
            public Account login(OAuthProvider provider, CancellationToken cancellation) throws Exception {
                return context.getAuth().login(provider, cancellation);
            }
            public void logout(UUID accountId, Deadline deadline) throws Exception { context.getAuth().logout(accountId, deadline); }
            public InboxService inbox() { return context.getInbox(); }
            public MailSendService sender() { return context.getSender(); }
            public void close() throws Exception { context.close(); }
        };
    }
}
