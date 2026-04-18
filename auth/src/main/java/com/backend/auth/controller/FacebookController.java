package com.backend.auth.controller;

import com.backend.auth.annotation.FacebookSignatureValid;
import com.backend.auth.dto.FacebookDeletionRequest;
import com.backend.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class FacebookController {

    private final AuthService authService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.oauth2.facebook.deletion-confirmation-url:https://your-domain.com/deletion-confirmation}")
    private String deletionConfirmationUrl;

    @Value("${app.kafka.topics.user-deleted:user.deleted}")
    private String userDeletedTopic;

    /**
     * Public endpoint - Privacy Policy
     */
    @GetMapping("/privacy-policy")
    public ResponseEntity<String> getPrivacyPolicy() {
        String htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Plant App - Privacy Policy</title>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                            max-width: 900px;
                            margin: 0 auto;
                            padding: 20px;
                            line-height: 1.6;
                            color: #333;
                        }
                        h1 { color: #2c5f2d; border-bottom: 3px solid #4caf50; padding-bottom: 10px; }
                        h2 { color: #2c5f2d; margin-top: 30px; }
                        .last-updated { color: #666; font-style: italic; }
                        ul { padding-left: 20px; }
                        li { margin: 8px 0; }
                        .contact { background: #f5f5f5; padding: 15px; border-radius: 5px; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <h1>🌱 Plant App - Privacy Policy</h1>
                    <p class="last-updated"><strong>Last Updated:</strong> April 2026</p>

                    <h2>1. Introduction</h2>
                    <p>Plant App ("we", "us", "our") respects your privacy. This Privacy Policy explains how we collect, use, and protect your personal data.</p>

                    <h2>2. Data We Collect</h2>
                    <ul>
                        <li><strong>From Facebook Login:</strong> Name, Email, Profile Picture, Facebook ID</li>
                        <li><strong>From Google Login:</strong> Name, Email, Profile Picture, Google ID</li>
                        <li><strong>From App Usage:</strong>
                            <ul>
                                <li>Plant scan history and results</li>
                                <li>Community posts, comments, and interactions</li>
                                <li>Search queries and history</li>
                                <li>Notification preferences</li>
                                <li>Device information and IP address</li>
                            </ul>
                        </li>
                    </ul>

                    <h2>3. How We Use Your Data</h2>
                    <ul>
                        <li>To authenticate your account and provide app services</li>
                        <li>To personalize your plant identification experience</li>
                        <li>To manage community features and interactions</li>
                        <li>To send notifications you've subscribed to</li>
                        <li>To improve our services and app performance</li>
                        <li>To comply with legal obligations</li>
                    </ul>

                    <h2>4. Data Storage & Security</h2>
                    <ul>
                        <li>Data is stored in encrypted PostgreSQL databases</li>
                        <li>We use JWT tokens for authentication (tokens expire after 30 days)</li>
                        <li>All API communication uses HTTPS encryption</li>
                        <li>Access is restricted to internal services only</li>
                    </ul>

                    <h2>5. Third-Party Services</h2>
                    <ul>
                        <li><strong>Firebase:</strong> Used for push notifications and media storage</li>
                        <li><strong>Facebook & Google:</strong> OAuth 2.0 authentication providers</li>
                        <li><strong>Elasticsearch:</strong> For search functionality</li>
                    </ul>

                    <h2>6. Your Rights & Data Deletion</h2>
                    <p><strong>You have the right to:</strong></p>
                    <ul>
                        <li>Access your personal data</li>
                        <li>Download your data in a portable format</li>
                        <li>Request deletion of your account and all associated data</li>
                        <li>Opt-out of marketing communications</li>
                    </ul>
                    <p><strong>To delete your account:</strong> Use the in-app settings or contact our support.</p>

                    <h2>7. Data Retention</h2>
                    <ul>
                        <li>Active user data: Retained while account is active</li>
                        <li>Deleted user data: Permanently removed within 30 days</li>
                        <li>Inactive accounts: May be deleted after 2 years of inactivity</li>
                    </ul>

                    <h2>8. Cookie Policy</h2>
                    <p>We do not use tracking cookies. We only use session tokens (JWT) for authentication.</p>

                    <h2>9. Changes to This Policy</h2>
                    <p>We may update this Privacy Policy periodically. Changes will be posted here with an updated "Last Updated" date.</p>

                    <h2>10. Contact Us</h2>
                    <div class="contact">
                        <p><strong>Questions about this Privacy Policy?</strong></p>
                        <p>Email: privacy@plantapp.com</p>
                        <p>Address: Plant App Development Team</p>
                    </div>
                </body>
                </html>
                """;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }

    /**
     * Facebook Data Deletion Callback
     * 
     * SECURITY: Protected by @FacebookSignatureValid annotation
     * - Verifies HMAC-SHA256 signature using app secret
     * - Only requests from Facebook (with valid signature) will reach this method
     * - Invalid signatures are rejected at aspect level (returns 401 Unauthorized)
     * 
     * Facebook will POST to this endpoint when user requests data deletion
     * 
     * Request format:
     * {
     * "signed_request": "signature.payload" (base64url encoded, HMAC-SHA256 signed)
     * }
     * 
     * Payload contains:
     * {
     * "user_id": "facebook_user_id",
     * "issued_at": timestamp
     * }
     */
    @FacebookSignatureValid(signedRequestParamName = "signed_request")
    @PostMapping("/facebook/delete-data")
    public ResponseEntity<?> handleFacebookDataDeletion(@RequestBody Map<String, String> request) {
        try {
            String signedRequest = request.get("signed_request");

            if (signedRequest == null || signedRequest.isEmpty()) {
                log.error("Missing signed_request in request body");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing signed_request"));
            }

            // Extract user ID from signed_request payload
            // Signature already verified by @FacebookSignatureValid aspect
            String facebookUserId = extractFacebookUserId(signedRequest);

            if (facebookUserId == null || facebookUserId.isEmpty()) {
                log.error("Could not extract user_id from Facebook signed_request");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid signed_request format"));
            }

            log.info("Facebook deletion request for user ID: {}", facebookUserId);

            // Delete user account and cascade delete all data
            authService.deleteUserByFacebookId(facebookUserId);

            // Publish Kafka event for other services to listen and cascade delete
            publishUserDeletionEvent(facebookUserId);

            // Generate confirmation code (required by Facebook)
            String confirmationCode = UUID.randomUUID().toString();

            return ResponseEntity.ok(Map.of(
                    "url", deletionConfirmationUrl + "/" + confirmationCode,
                    "confirmation_code", confirmationCode));

        } catch (Exception e) {
            log.error("Error processing Facebook data deletion", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Extract user ID from Facebook signed_request payload
     * Format: "signature.payload" where payload is base64url encoded JSON
     * 
     * @param signedRequest Facebook signed_request (signature already verified by
     *                      aspect)
     * @return Facebook user ID, or null if not found
     */
    private String extractFacebookUserId(String signedRequest) {
        try {
            String[] parts = signedRequest.split("\\.");
            if (parts.length != 2) {
                return null;
            }

            String payload = parts[1];
            byte[] decodedPayload = base64UrlDecode(payload);
            String decodedJson = new String(decodedPayload, StandardCharsets.UTF_8);

            // Extract user_id from JSON
            return extractJsonValue(decodedJson, "user_id");

        } catch (Exception e) {
            log.error("Error extracting Facebook user ID from signed_request", e);
            return null;
        }
    }

    /**
     * Decode base64url encoded string
     * Handles padding and character conversion (-_ to +/)
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

    /**
     * Extract value from JSON string
     * Handles simple string values in JSON
     * 
     * @param json JSON string
     * @param key  Key to search for
     * @return Value associated with key, or null if not found
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1)
            return null;

        startIndex += searchKey.length();
        int endIndex = json.indexOf(",", startIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf("}", startIndex);
        }

        String value = json.substring(startIndex, endIndex).trim();
        // Remove quotes if it's a string value
        return value.replaceAll("^\"|\"$", "");
    }

    /**
     * Publish user deletion event to Kafka
     * Other services (community, scan, user) will listen and cascade delete
     */
    private void publishUserDeletionEvent(String userId) {
        try {
            // Event format: simple JSON with userId
            String event = String.format("{\"userId\": \"%s\", \"timestamp\": %d}",
                    userId, System.currentTimeMillis());

            kafkaTemplate.send(userDeletedTopic, userId, event);
            log.info("Published user deletion event to Kafka topic: {} for user: {}",
                    userDeletedTopic, userId);
        } catch (Exception e) {
            log.error("Failed to publish user deletion event to Kafka", e);
            // Don't throw - deletion already happened, just log the error
        }
    }
}
