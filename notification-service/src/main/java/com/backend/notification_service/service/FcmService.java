package com.backend.notification_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.FcmToken;
import com.backend.notification_service.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final FcmTokenRepository fcmTokenRepo;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2000L;

    // In-memory retry queue — cleared on restart, acceptable for notifications
    private final LinkedBlockingQueue<RetryItem> retryQueue = new LinkedBlockingQueue<>();

    @lombok.Value
    private static class RetryItem {
        String fcmToken;
        UUID userId;
        String title;
        String body;
        Map<String, String> data;
        int retryCount;
        long nextRetryTime;

        RetryItem(String fcmToken, UUID userId, String title, String body, Map<String, String> data) {
            this.fcmToken = fcmToken;
            this.userId = userId;
            this.title = title;
            this.body = body;
            this.data = data;
            this.retryCount = 0;
            this.nextRetryTime = System.currentTimeMillis() + INITIAL_BACKOFF_MS;
        }

        RetryItem withNextRetry(int newRetryCount) {
            long nextRetry = System.currentTimeMillis() + (INITIAL_BACKOFF_MS << newRetryCount);
            return new RetryItem(fcmToken, userId, title, body, data, newRetryCount, nextRetry);
        }

        private RetryItem(String fcmToken, UUID userId, String title, String body,
                Map<String, String> data, int retryCount, long nextRetryTime) {
            this.fcmToken = fcmToken;
            this.userId = userId;
            this.title = title;
            this.body = body;
            this.data = data;
            this.retryCount = retryCount;
            this.nextRetryTime = nextRetryTime;
        }
    }

    /**
     * Send notification to all FCM tokens registered for a user.
     * Failed sends are queued for retry with exponential backoff.
     */
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        List<FcmToken> tokens = fcmTokenRepo.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens found for userId={}", userId);
            return;
        }

        List<String> failedTokens = sendToTokens(tokens, title, body, data);

        if (!failedTokens.isEmpty()) {
            failedTokens.forEach(token -> retryQueue.offer(new RetryItem(token, userId, title, body, data)));
            log.warn("Queued {} failed tokens for retry | userId={}", failedTokens.size(), userId);
        }
    }

    /**
     * Send to each token — removes expired tokens, collects transient failures.
     */
    private List<String> sendToTokens(List<FcmToken> tokens, String title,
            String body, Map<String, String> data) {
        List<String> failedTokens = new ArrayList<>();

        for (FcmToken fcmToken : tokens) {
            try {
                FirebaseMessaging.getInstance().send(
                        buildMessage(fcmToken.getToken(), title, body, data));
                log.debug("FCM sent | token={}", fcmToken.getToken());

            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    // Token expired — delete immediately, no retry needed
                    fcmTokenRepo.deleteByToken(fcmToken.getToken());
                    log.warn("Removed expired FCM token | token={}", fcmToken.getToken());
                } else {
                    // Transient failure — queue for retry
                    failedTokens.add(fcmToken.getToken());
                    log.warn("FCM send failed (will retry) | token={} error={}",
                            fcmToken.getToken(), e.getMessage());
                }
            }
        }
        return failedTokens;
    }

    /**
     * Scheduled retry processor — runs every 5 seconds.
     * Single-threaded via @Scheduled, no concurrent processor risk.
     */
    @Scheduled(fixedDelay = 5000)
    public void processRetryQueue() {
        if (retryQueue.isEmpty())
            return;

        long now = System.currentTimeMillis();
        int processed = 0;

        // Snapshot current size to avoid infinite loop if items keep re-queuing
        int size = retryQueue.size();

        for (int i = 0; i < size; i++) {
            RetryItem item = retryQueue.poll();
            if (item == null)
                break;

            // Not yet ready — put back and skip
            if (now < item.nextRetryTime) {
                retryQueue.offer(item);
                continue;
            }

            if (item.retryCount >= MAX_RETRIES) {
                log.error("FCM delivery failed after {} retries | token={} userId={}",
                        MAX_RETRIES, item.fcmToken, item.userId);
                continue;
            }

            try {
                FirebaseMessaging.getInstance().send(
                        buildMessage(item.fcmToken, item.title, item.body, item.data));
                log.info("FCM retry successful | token={} attempt={}",
                        item.fcmToken, item.retryCount + 1);
                processed++;

            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    // Token expired during retry — remove, no more retries
                    fcmTokenRepo.deleteByToken(item.fcmToken);
                    log.warn("Removed expired FCM token during retry | token={}", item.fcmToken);
                } else {
                    // Re-queue with incremented retry count and next backoff time
                    retryQueue.offer(item.withNextRetry(item.retryCount + 1));
                    log.warn("FCM retry failed | token={} attempt={} nextRetryMs={}",
                            item.fcmToken, item.retryCount + 1,
                            INITIAL_BACKOFF_MS << (item.retryCount + 1));
                }
            }
        }

        if (processed > 0) {
            log.debug("FCM retry processed {} items | remaining={}", processed, retryQueue.size());
        }
    }

    @Transactional
    public void registerFcmToken(UUID userId, String token, String deviceInfo) {
        fcmTokenRepo.upsertToken(userId, token, deviceInfo);
    }

    @Transactional
    public void deleteByToken(String token) {
        fcmTokenRepo.deleteByToken(token);
    }

    private Message buildMessage(String token, String title, String body, Map<String, String> data) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Map.of())
                .build();
    }
}