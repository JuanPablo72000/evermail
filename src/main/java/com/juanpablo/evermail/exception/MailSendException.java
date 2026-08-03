package com.juanpablo.evermail.exception;

public class MailSendException extends EvermailException {

    public MailSendException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MailSendException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public MailSendException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public MailSendException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}