package com.juanpablo.evermail.exception;

public class ConfigurationException extends EvermailException {
    public ConfigurationException(ErrorCode code, String message) {
        super(code, message);
    }

    public ConfigurationException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
