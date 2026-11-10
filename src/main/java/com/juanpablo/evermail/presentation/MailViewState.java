package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.navigation.NavigationRules.Route;
import java.util.*;

/** Immutable snapshots shared with the view; no JavaFX properties or controls. */
public record MailViewState(Route route, SessionSnapshot session, boolean sessionBusy, OperationError sessionError,
                            Inbox inbox, Reader reader, Compose compose, boolean logoutConfirmation,
                            Map<String, AvatarPhoto> avatars, boolean avatarPermissionRequired) {
    public MailViewState { avatars = Map.copyOf(avatars); }
    public MailViewState(Route route, SessionSnapshot session, boolean sessionBusy, OperationError sessionError,
                         Inbox inbox, Reader reader, Compose compose, boolean logoutConfirmation) {
        this(route, session, sessionBusy, sessionError, inbox, reader, compose, logoutConfirmation, Map.of(), false);
    }
    public boolean canReadPrevious() { return adjacentMail(-1).isPresent(); }
    public boolean canReadNext() { return adjacentMail(1).isPresent(); }

    /** Follow the displayed inbox order, including pages explicitly loaded by the user. */
    public Optional<UUID> adjacentMail(int offset) {
        if (route != Route.READER || sessionBusy || !session.hasSession()) return Optional.empty();
        for (int index = 0; index < inbox.items().size(); index++) {
            if (inbox.items().get(index).getId().equals(reader.mailId())) {
                int target = index + offset;
                return target >= 0 && target < inbox.items().size()
                        ? Optional.of(inbox.items().get(target).getId()) : Optional.empty();
            }
        }
        return Optional.empty();
    }
    public record Inbox(List<MailHeader> items, boolean loading, boolean hasMore, boolean stale, OperationError error) {
        public Inbox { items = List.copyOf(items); }
        public boolean empty() { return !loading && error == null && items.isEmpty(); }
        public boolean canLoadMore() { return !loading && hasMore; }
    }
    public enum ReaderPhase { EMPTY, HEADER_LOADING, CONTENT_LOADING, READY, ERROR }
    public enum BodyMode { TEXT, HTML }
    public record Reader(UUID mailId, MailHeader header, MailPresentation content,
                         ReaderPhase phase, BodyMode mode, OperationError error, boolean imagesLoading) {
        public Reader(UUID mailId, MailHeader header, MailPresentation content,
                      ReaderPhase phase, BodyMode mode, OperationError error) {
            this(mailId, header, content, phase, mode, error, false);
        }
        public boolean hasHtml() { return content != null && content.getDocument() != null; }
    }
    public enum SendPhase { EDITING, SENDING, SENT, ACCEPTED, UNKNOWN, FAILED }
    public record Compose(ComposeDraft draft, SendPhase phase, OperationError error, boolean discardConfirmation) {
        public boolean editable() { return phase == SendPhase.EDITING || phase == SendPhase.FAILED || phase == SendPhase.SENT; }
        public boolean canSend() { return editable() && !draft.isEmpty(); }
    }
    @Override public String toString() { return "MailViewState[route=" + route + "]"; }
}
