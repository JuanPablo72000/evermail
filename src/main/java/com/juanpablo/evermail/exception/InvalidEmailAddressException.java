package com.juanpablo.evermail.exception;

public class InvalidEmailAddressException extends InvalidFieldException {

    public InvalidEmailAddressException(String fieldName) {
        super(fieldName);
    }

    public InvalidEmailAddressException(String fieldName, String message) {
        super(fieldName, message);
    }

    public InvalidEmailAddressException(String fieldName, Throwable cause) {
        super(fieldName, cause);
    }

    public InvalidEmailAddressException(String fieldName, String message, Throwable cause) {
        super(fieldName, message, cause);
    }
}