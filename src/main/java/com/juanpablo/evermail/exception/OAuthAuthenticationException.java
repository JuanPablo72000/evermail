package com.juanpablo.evermail.exception;

public class OAuthAuthenticationException extends EvermailException {

    public OAuthAuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public OAuthAuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public OAuthAuthenticationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public OAuthAuthenticationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}