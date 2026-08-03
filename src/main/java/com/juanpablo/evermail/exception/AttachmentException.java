package com.juanpablo.evermail.exception;

public class AttachmentException extends EvermailException {

    public AttachmentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AttachmentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AttachmentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AttachmentException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}