package com.backend.notification_service.service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.backend.notification_service.dto.NotificationResponse;
import com.backend.notification_service.exception.AppException;
import com.backend.notification_service.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SseService {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        Runnable cleanup = () -> removeEmitter(userId, emitter);

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("SSE subscribed userId={} total={}", userId, emitters.get(userId).size());

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    public void sendToUser(UUID userId, NotificationResponse notification) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty())
            return;

        list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                removeEmitter(userId, emitter); // dùng method chung, không getOrDefault
                log.debug("SSE emitter removed for userId={} — connection closed", userId);
            }
        });
    }

    // Extract ra method để tái sử dụng, tránh duplicate logic
    private void removeEmitter(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty())
                emitters.remove(userId);
        }
    }
}