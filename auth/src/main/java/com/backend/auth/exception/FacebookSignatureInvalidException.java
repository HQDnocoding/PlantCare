package com.backend.auth.exception;

/**
 * Exception thrown when Facebook signed_request signature verification fails
 * 
 * This indicates either:
 * 1. The request is not from Facebook
 * 2. The request has been tampered with
 * 3. The FACEBOOK_APP_SECRET is not configured correctly
 */
public class FacebookSignatureInvalidException extends RuntimeException {

    public FacebookSignatureInvalidException(String message) {
        super(message);
    }

    public FacebookSignatureInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
