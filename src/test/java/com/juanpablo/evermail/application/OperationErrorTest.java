package com.juanpablo.evermail.application;

import com.juanpablo.evermail.exception.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationErrorTest {
    @Test void identifiesNestedInboxFailuresWithoutExposingServerMessages() {
        Exception[] causes = {
                new jakarta.mail.AuthenticationFailedException("secret-token-and-mail-body"),
                new jakarta.mail.NoSuchProviderException("secret-token-and-mail-body"),
                new java.net.UnknownHostException("secret-token-and-mail-body"),
                new javax.net.ssl.SSLHandshakeException("secret-token-and-mail-body"),
                new java.net.SocketTimeoutException("secret-token-and-mail-body"),
                new java.net.ConnectException("secret-token-and-mail-body")
        };
        String[] expected = {"rechazó", "componente IMAP", "DNS", "conexión segura", "tiempo de espera", "993"};
        for (int i = 0; i < causes.length; i++) {
            var wrapped = new jakarta.mail.MessagingException("secret-token-and-mail-body", causes[i]);
            var error = OperationError.from(new MailFetchException(ErrorCode.IMAP_CONNECTION_FAILED, wrapped));
            assertEquals(ErrorCode.IMAP_CONNECTION_FAILED, error.code());
            assertTrue(error.message().contains(expected[i]), error.message());
            assertFalse(error.message().contains("secret"));
        }
    }

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
