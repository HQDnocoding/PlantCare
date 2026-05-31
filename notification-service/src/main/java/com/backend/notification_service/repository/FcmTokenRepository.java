package com.backend.notification_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.FcmToken;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    List<FcmToken> findByUserId(UUID userId);

    Optional<FcmToken> findByToken(String token);

    @Transactional
    void deleteByToken(String token);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO fcm_tokens (id, user_id, token, device_info, created_at)
            VALUES (gen_random_uuid(), :userId, :token, :deviceInfo, now())
            ON CONFLICT (token) DO UPDATE
            SET user_id = :userId,
                device_info = :deviceInfo
            """, nativeQuery = true)
    void upsertToken(
            @Param("userId") UUID userId,
            @Param("token") String token,
            @Param("deviceInfo") String deviceInfo);
}