package com.juanpablo.evermail.exception;

public class MailFetchException extends EvermailException {

    public MailFetchException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MailFetchException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public MailFetchException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public MailFetchException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}