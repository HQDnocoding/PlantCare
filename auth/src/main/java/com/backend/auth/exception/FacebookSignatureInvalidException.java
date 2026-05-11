package com.backend.auth.exception;

public class FacebookSignatureInvalidException extends RuntimeException {

    public FacebookSignatureInvalidException(String message) {
        super(message);
    }

    public FacebookSignatureInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
