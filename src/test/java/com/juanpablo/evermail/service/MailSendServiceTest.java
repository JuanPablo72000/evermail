package com.juanpablo.evermail.service;

import com.juanpablo.evermail.dao.Sql;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.support.BackendFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MailSendServiceTest extends BackendFixture {
    @Test
    void successfulSubmissionIsIdempotentAndNotInInbox() throws Exception {
        ComposeRequest request = request(UUID.randomUUID());
        SendResult first = sender.send(request, budget());
        SendResult second = sender.send(request, budget());
        assertEquals(DeliveryState.RECORDED, first.getState());
        assertEquals(first.getSentMailId(), second.getSentMailId());
        assertEquals(1, gateway.submissions.get());
        assertEquals(1, count("mail"));
        assertTrue(mails.readInbox(account.getId(), null, 50).getItems().isEmpty());
        assertEquals("Private body", mails.readContent(account.getId(), first.getSentMailId()).getPlainText());
    }

    @Test
    void duplicateRecipientsInDifferentRolesFailBeforeSmtp() {
        ComposeRequest request = new ComposeRequest(UUID.randomUUID(), account.getId(), "", "",
                List.of(recipient("one@example.com", RecipientType.TO), recipient("one@example.com", RecipientType.CC)));
        assertThrows(ValidationException.class, () -> sender.send(request, budget()));
        assertEquals(0, gateway.submissions.get());
    }

    @Test
    void duplicateRecipientsSameRoleAreConsolidated() throws Exception {
        ComposeRequest request = new ComposeRequest(UUID.randomUUID(), account.getId(), "", "",
                List.of(recipient("one@EXAMPLE.com", RecipientType.TO), recipient("one@example.com", RecipientType.TO)));
        sender.send(request, budget());
        assertEquals(1, count("mail_recipient"));
    }

    @Test
    void ambiguousNetworkFailureNeverResends() throws Exception {
        gateway.throwDuringSend = true;
        ComposeRequest request = request(UUID.randomUUID());
        assertEquals(DeliveryState.UNKNOWN, sender.send(request, budget()).getState());
        assertEquals(DeliveryState.UNKNOWN, sender.send(request, budget()).getState());
        sender.recover(account.getId());
        assertEquals(1, gateway.submissions.get());
        assertEquals(0, count("mail"));
    }

    @Test
    void acceptedSmtpWithLocalFailureRecoversWithoutAnotherDelivery() throws Exception {
        transactions.write(c -> { Sql.update(c, "CREATE TRIGGER fail_sent BEFORE INSERT ON mail BEGIN SELECT RAISE(ABORT,'test'); END"); return null; });
        ComposeRequest request = request(UUID.randomUUID());
        SendResult pending = sender.send(request, budget());
        assertEquals(DeliveryState.ACCEPTED, pending.getState());
        assertEquals(ErrorCode.LOCAL_SAVE_PENDING, pending.getError());
        assertEquals(0, count("mail"));
        assertEquals(0, count("mail_recipient"));
        transactions.write(c -> { Sql.update(c, "DROP TRIGGER fail_sent"); return null; });
        sender.recover(account.getId());
        assertEquals(DeliveryState.RECORDED, outbox.find(account.getId(), request.getSubmissionId()).getState());
        assertEquals(1, gateway.submissions.get());
        assertEquals(1, count("mail"));
    }

    @Test
    void failedAcknowledgementWriteLeavesRecoverableUnknown() throws Exception {
        transactions.write(c -> { Sql.update(c, "CREATE TRIGGER fail_ack BEFORE UPDATE OF state ON outbox_message WHEN NEW.state='ACCEPTED' BEGIN SELECT RAISE(ABORT,'test'); END"); return null; });
        ComposeRequest request = request(UUID.randomUUID());
        assertEquals(DeliveryState.UNKNOWN, sender.send(request, budget()).getState());
        transactions.write(c -> { Sql.update(c, "DROP TRIGGER fail_ack"); return null; });
        sender.recover(account.getId());
        assertEquals(DeliveryState.UNKNOWN, outbox.find(account.getId(), request.getSubmissionId()).getState());
        sender.send(request, budget());
        assertEquals(1, gateway.submissions.get());
    }

    @Test
    void submissionIdCannotBeReusedForDifferentBody() throws Exception {
        ComposeRequest request = request(UUID.randomUUID());
        sender.send(request, budget());
        ComposeRequest different = new ComposeRequest(request.getSubmissionId(), request.getAccountId(), "", "changed", request.getRecipients());
        ValidationException error = assertThrows(ValidationException.class, () -> sender.send(different, budget()));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, error.getErrorCode());
        assertEquals(1, gateway.submissions.get());
    }

    @Test
    void concurrentRequestsSendOnlyOnce() throws Exception {
        ComposeRequest request = request(UUID.randomUUID());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<SendResult> a = pool.submit(() -> sender.send(request, budget()));
            Future<SendResult> b = pool.submit(() -> sender.send(request, budget()));
            assertEquals(DeliveryState.RECORDED, a.get().getState());
            assertEquals(DeliveryState.RECORDED, b.get().getState());
        }
        assertEquals(1, gateway.submissions.get());
    }

    @Test
    void interruptedSendingIsMarkedUnknownAtStartup() throws Exception {
        ComposeRequest request = request(UUID.randomUUID());
        outbox.prepare(request);
        assertTrue(outbox.claim(account.getId(), request.getSubmissionId()));
        sender.recover(account.getId());
        assertEquals(DeliveryState.UNKNOWN, outbox.find(account.getId(), request.getSubmissionId()).getState());
        assertEquals(0, gateway.submissions.get());
    }
}
