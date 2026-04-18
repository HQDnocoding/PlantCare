package com.backend.auth.repository;

import com.backend.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing RefreshToken persistence.
 * Includes specialized queries for security operations like global logout
 * and database maintenance.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

        /**
         * Finds a token by its SHA-256 hash.
         * 
         * @param tokenHash The hashed version of the raw opaque token.
         * @return Optional containing the token if found.
         */
        Optional<RefreshToken> findByTokenHash(String tokenHash);

        /**
         * Revokes all active refresh tokens for a specific user.
         * Effectively performs a "Global Logout" or "Logout from all devices".
         *
         * @param userId The ID of the user to log out.
         * @param now    The current timestamp to set as revocation time.
         * @return Number of tokens revoked.
         */
        @Modifying
        @Query("""
                        UPDATE RefreshToken rt
                        SET rt.revokedAt = :now
                        WHERE rt.user.id = :userId
                          AND rt.revokedAt IS NULL
                        """)
        int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

        /**
         * Cleanup task to remove unusable tokens from the database.
         * Targets tokens that are either expired or have been manually revoked.
         *
         * @param userId The ID of the user.
         * @param cutoff The timestamp threshold for expiration.
         * @return Number of records deleted.
         */
        @Modifying
        @Query("""
                        DELETE FROM RefreshToken rt
                        WHERE rt.user.id = :userId
                          AND (rt.expiresAt < :cutoff OR rt.revokedAt IS NOT NULL)
                        """)
        int deleteExpiredOrRevokedByUserId(
                        @Param("userId") UUID userId,
                        @Param("cutoff") OffsetDateTime cutoff);
}