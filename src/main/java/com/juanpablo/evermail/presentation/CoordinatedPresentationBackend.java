package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.MailPresentationService;
import java.util.*;
import java.util.concurrent.CompletionStage;

public final class CoordinatedPresentationBackend implements PresentationBackend {
    private final ApplicationCoordinator coordinator;
    public CoordinatedPresentationBackend(ApplicationCoordinator coordinator) { this.coordinator = Objects.requireNonNull(coordinator); }
    public SessionSnapshot session() { return coordinator.session(); }
    public OperationHandle<StartupResult> start() { return coordinator.start(); }
    public OperationHandle<Account> login(OAuthProvider provider) { return coordinator.login(provider); }
    public OperationHandle<Void> logout() { return coordinator.logout(); }
    public OperationHandle<InboxPage> inbox(InboxAction action, InboxCursor cursor) {
        return coordinator.submit(AppConstants.INBOX_BUDGET, (b, a, d) -> {
            d.check();
            InboxPage page = switch (action) {
                case CACHE -> b.inbox().readCached(a.getId(), cursor);
                case REFRESH -> b.inbox().refresh(a.getId(), d);
                case MORE -> b.inbox().loadMore(a.getId(), cursor, d);
            };
            d.check();
            return page;
        });
    }
    public OperationHandle<MailHeader> header(UUID mailId) {
        return coordinator.submit(AppConstants.OPEN_HEADER_BUDGET, (b, a, d) -> {
            d.check();
            MailHeader header = b.inbox().openHeader(a.getId(), mailId);
            d.check();
            return header;
        });
    }
    public OperationHandle<MailPresentation> content(UUID mailId) {
        return coordinator.submit(AppConstants.CONTENT_BUDGET, (b, a, d) -> {
            MailContent content = b.inbox().loadContent(a.getId(), mailId, d);
            MailPresentation presentation = new MailPresentationService().prepare(content, d);
            b.inbox().markRead(a.getId(), mailId);
            d.check();
            return presentation;
        });
    }
    public OperationHandle<SendResult> send(UUID submissionId, ComposeDraft draft) {
        return coordinator.submit(AppConstants.SEND_BUDGET,
                (b, a, d) -> b.sender().send(draft.request(a.getId(), submissionId), d));
    }
    public CompletionStage<OperationResult<Void>> closeAsync() { return coordinator.closeAsync(); }
}
