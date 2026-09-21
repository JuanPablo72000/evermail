package com.juanpablo.evermail.service;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InboxServiceTest extends BackendFixture {
    @Test
    void openingCachedBodyMakesNoRemoteRequest() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        UUID id = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        mails.saveContent(account.getId(), id, new RemoteMailContent("Cached", List.of()));
        InboxService inbox = new InboxService(gateway, mails, coordinator);
        assertEquals("Cached", inbox.loadContent(account.getId(), id, budget()).getPlainText());
        assertEquals(0, gateway.inboxRequests.get());
    }

    @Test
    void downloadedBodyIsReusedOnNextOpen() throws Exception {
        mails.saveInboxPage(account.getId(), page(1, 1, 1, false));
        UUID id = mails.readInbox(account.getId(), null, 50).getItems().getFirst().getId();
        InboxService inbox = new InboxService(gateway, mails, coordinator);
        assertEquals("Downloaded", inbox.loadContent(account.getId(), id, budget()).getPlainText());
        assertEquals("Downloaded", inbox.loadContent(account.getId(), id, budget()).getPlainText());
        assertEquals(1, gateway.inboxRequests.get());
    }

    @Test
    void loadMoreFetchesOlderMessagesAndReturnsOnlyNextPage() throws Exception {
        gateway.remotePage = page(5, 51, 100, true);
        InboxService inbox = new InboxService(gateway, mails, coordinator);
        InboxPage first = inbox.refresh(account.getId(), budget());
        gateway.remotePage = page(5, 1, 50, false);
        InboxPage second = inbox.loadMore(account.getId(), first.getNextCursor(), budget());
        assertEquals(50, second.getItems().size());
        assertEquals(50, second.getItems().getFirst().getRemoteId().getUid());
        assertEquals(2, gateway.inboxRequests.get());
        inbox.loadMore(account.getId(), first.getNextCursor(), budget());
        assertEquals(2, gateway.inboxRequests.get());
    }
}
