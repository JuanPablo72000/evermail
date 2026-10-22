package com.juanpablo.evermail.presentation;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.service.ComposeService;
import com.juanpablo.evermail.exception.ValidationException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ComposeDraftTest {
    @Test void mapsAddressesAndRolesWithoutAcceptingAnEditableSender() throws Exception {
        UUID account = UUID.randomUUID(), submission = UUID.randomUUID();
        ComposeRequest request = new ComposeDraft("a@example.com; b@example.com", "c@example.com", "d@example.com", "Subject", "Body")
                .request(account, submission);
        ComposeRequest valid = new ComposeService().validate(request);
        assertEquals(account, valid.getAccountId()); assertEquals(submission, valid.getSubmissionId());
        assertEquals(List.of(RecipientType.TO, RecipientType.TO, RecipientType.CC, RecipientType.BCC),
                valid.getRecipients().stream().map(Recipient::getType).toList());
    }
    @Test void leavesValidationToTheExistingBackendWithoutSilentlyDroppingInvalidAddresses() {
        ComposeRequest request = new ComposeDraft("valid@example.com, invalid", "", "", "Subject", "Body")
                .request(UUID.randomUUID(), UUID.randomUUID());
        assertThrows(ValidationException.class, () -> new ComposeService().validate(request));
    }
    @Test void normalizesAbsentFieldsAndDoesNotExposeDraftInLogs() {
        assertEquals(ComposeDraft.empty(), new ComposeDraft(null, null, null, null, null));
        assertFalse(new ComposeDraft("secret@example.com", "", "", "Secret", "Private").toString().contains("Private"));
    }
}
