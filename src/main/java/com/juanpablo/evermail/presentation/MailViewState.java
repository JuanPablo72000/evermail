package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.application.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.navigation.NavigationRules.Route;
import java.util.*;

/** Immutable snapshots for a future view; no JavaFX properties or controls. */
public record MailViewState(Route route, SessionSnapshot session, boolean sessionBusy, OperationError sessionError,
                            Inbox inbox, Reader reader, Compose compose, boolean logoutConfirmation) {
    public record Inbox(List<MailHeader> items, boolean loading, boolean hasMore, boolean stale, OperationError error) {
        public Inbox { items = List.copyOf(items); }
        public boolean empty() { return !loading && error == null && items.isEmpty(); }
        public boolean canLoadMore() { return !loading && hasMore; }
    }
    public enum ReaderPhase { EMPTY, HEADER_LOADING, CONTENT_LOADING, READY, ERROR }
    public enum BodyMode { TEXT, HTML }
    public record Reader(UUID mailId, MailHeader header, MailPresentation content,
                         ReaderPhase phase, BodyMode mode, OperationError error) {
        public boolean hasHtml() { return content != null && content.getDocument() != null; }
    }
    public enum SendPhase { EDITING, SENDING, SENT, ACCEPTED, UNKNOWN, FAILED }
    public record Compose(ComposeDraft draft, SendPhase phase, OperationError error, boolean discardConfirmation) {
        public boolean editable() { return phase == SendPhase.EDITING || phase == SendPhase.FAILED || phase == SendPhase.SENT; }
        public boolean canSend() { return editable() && !draft.isEmpty(); }
    }
    @Override public String toString() { return "MailViewState[route=" + route + "]"; }
}
