package com.juanpablo.evermail.application;

import com.juanpablo.evermail.exception.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationErrorTest {
    @Test void translatesEveryDomainCodeWithoutExposingOriginalMessages() {
        for (ErrorCode code : ErrorCode.values()) {
            OperationError error = OperationError.from(new SessionException(code, "secret-token-and-mail-body"));
            assertEquals(code, error.code());
            assertFalse(error.message().isBlank());
            assertFalse(error.message().contains("secret"));
        }
    }

    @Test void distinguishesCancellationFromTimeout() {
        assertEquals(ErrorCode.CANCELLED, OperationError.from(new InterruptedException()).code());
        assertEquals(ErrorCode.LOCAL_TIMEOUT,
                OperationError.from(new SessionException(ErrorCode.LOCAL_TIMEOUT, "internal")).code());
    }

    @Test void uncertainDeliveryDoesNotSuggestAnAutomaticResend() {
        String message = OperationError.from(new MailSendException(ErrorCode.DELIVERY_UNKNOWN, "internal")).message();
        assertTrue(message.contains("Comprueba"));
        assertTrue(OperationError.from(new MailSendException(ErrorCode.LOCAL_SAVE_PENDING, "internal"))
                .message().contains("No vuelvas a enviar"));
    }
}
