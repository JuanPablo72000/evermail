package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.model.*;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed async boundary, replaceable in presentation tests without running JavaFX. */
public interface PresentationBackend {
    enum InboxAction { CACHE, REFRESH, MORE }
    SessionSnapshot session();
    OperationHandle<StartupResult> start();
    OperationHandle<Account> login(OAuthProvider provider);
    OperationHandle<Void> logout();
    OperationHandle<InboxPage> inbox(InboxAction action, InboxCursor cursor);
    OperationHandle<MailHeader> header(UUID mailId);
    default void readerClosed(UUID mailId) { }
    OperationHandle<MailPresentation> content(UUID mailId);
    /** Optional second stage; implementations without remote images need no extra operation. */
    default java.util.Optional<OperationHandle<MailPresentation>> images(MailContent content) {
        return java.util.Optional.empty();
    }
    default java.util.Optional<OperationHandle<MailPresentation>> images(MailContent content,
            java.util.function.Consumer<MailPresentation> progress) { return images(content); }
    OperationHandle<SendResult> send(UUID submissionId, ComposeDraft draft);
    CompletionStage<OperationResult<Void>> closeAsync();
}
