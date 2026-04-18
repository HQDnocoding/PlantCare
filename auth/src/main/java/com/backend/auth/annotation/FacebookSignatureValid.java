package com.backend.auth.annotation;

import java.lang.annotation.*;

/**
 * Custom annotation to validate Facebook signed_request signature
 * Ensures that only requests from Facebook (with valid HMAC-SHA256 signature)
 * are accepted
 * 
 * Usage: Place on controller methods that receive Facebook callbacks
 * 
 * @FacebookSignatureValid
 *                         @PostMapping("/facebook/delete-data")
 *                         public ResponseEntity<?> handleFacebookCallback(...)
 *                         { }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FacebookSignatureValid {

    /**
     * Request parameter name containing the signed request
     * Default: "signed_request" (Facebook standard)
     */
    String signedRequestParamName() default "signed_request";

    /**
     * Whether to allow requests without signed_request parameter
     * Default: false (reject if no signature)
     */
    boolean optional() default false;
}
