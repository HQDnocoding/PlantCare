package com.backend.auth.interceptor;

import com.backend.auth.annotation.FacebookSignatureValid;
import com.backend.auth.exception.FacebookSignatureInvalidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Aspect to validate Facebook signed_request signature on methods annotated with @FacebookSignatureValid
 * 
 * This ensures that:
 * 1. Request contains a valid signed_request parameter
 * 2. Signature is verified using HMAC-SHA256 with app secret
 * 3. Only legitimate Facebook requests are processed
 * 
 * If signature is invalid, throws FacebookSignatureInvalidException (returns 401)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FacebookSignatureValidationAspect {

    @Value("${app.oauth2.facebook.app-secret:}")
    private String facebookAppSecret;

    /**
     * Intercept methods with @FacebookSignatureValid annotation
     * Validate the Facebook signed_request before method execution
     */
    @Before("@annotation(facebookSignatureValid)")
    public void validateFacebookSignature(JoinPoint joinPoint, FacebookSignatureValid facebookSignatureValid) {
        log.debug("Validating Facebook signature for method: {}", joinPoint.getSignature().getName());

        // Get current HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.error("No HTTP request context found");
            throw new FacebookSignatureInvalidException("No HTTP request context");
        }

        HttpServletRequest request = attributes.getRequest();
        String signedRequestParamName = facebookSignatureValid.signedRequestParamName();
        
        // Get signed_request from request body or parameters
        String signedRequest = request.getParameter(signedRequestParamName);
        
        // If not found in params, try to extract from request body (for POST with JSON)
        if (signedRequest == null || signedRequest.isEmpty()) {
            try {
                String contentType = request.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    // For JSON body, signed_request should be in JSON object
                    // This is handled by extracting from Map in controller
                    signedRequest = request.getAttribute("facebookSignedRequest") != null 
                        ? request.getAttribute("facebookSignedRequest").toString() 
                        : null;
                }
            } catch (Exception e) {
                log.warn("Error reading signed_request from request body", e);
            }
        }

        // Check if signature is present
        if (signedRequest == null || signedRequest.isEmpty()) {
            if (facebookSignatureValid.optional()) {
                log.debug("No signed_request found but marked as optional");
                return;
            }
            log.error("Missing signed_request parameter");
            throw new FacebookSignatureInvalidException("Missing signed_request parameter");

        }

        // Verify signature
        if (!verifyFacebookSignedRequest(signedRequest)) {
            log.error("Facebook signature verification failed for request from {}", request.getRemoteHost());
            throw new FacebookSignatureInvalidException("Invalid Facebook signature");
        }

        log.debug("Facebook signature validation successful");
    }

    /**
     * Verify Facebook signed_request
     * Format: "signature.payload" where both are base64url encoded
     * 
     * @param signedRequest Facebook signed_request string
     * @return true if signature is valid, false otherwise
     */
    private boolean verifyFacebookSignedRequest(String signedRequest) {
        try {
            String[] parts = signedRequest.split("\\.");
            if (parts.length != 2) {
                log.warn("Invalid signed_request format: expected 2 parts, got {}", parts.length);
                return false;
            }

            String signature = parts[0];
            String payload = parts[1];

            // Decode signature
            byte[] decodedSignature = base64UrlDecode(signature);

            // Verify HMAC-SHA256
            byte[] expectedSignature = hmacSha256(payload, facebookAppSecret);

            boolean isValid = Arrays.equals(decodedSignature, expectedSignature);
            
            if (!isValid) {
                log.warn("HMAC-SHA256 signature mismatch");
            }
            
            return isValid;

        } catch (Exception e) {
            log.error("Error verifying Facebook signature", e);
            return false;
        }
    }

    /**
     * Calculate HMAC-SHA256 signature
     */
    private byte[] hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode base64url encoded string
     * Handles padding and character conversion
     */
    private byte[] base64UrlDecode(String str) {
        String base64 = str
                .replace('-', '+')
                .replace('_', '/');
        int pad = 4 - (base64.length() % 4);
        if (pad != 4) {
            base64 += "=".repeat(pad);
        }
        return Base64.getDecoder().decode(base64);
    }
}
