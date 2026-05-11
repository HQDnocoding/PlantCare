package com.backend.auth.repository;

import com.backend.auth.domain.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    @Query("""
                SELECT o FROM OtpCode o
                WHERE o.phone = :phone
                  AND o.purpose = :purpose
                  AND o.expiresAt > :now
                  AND o.verifiedAt IS NULL
                  AND o.attempts < 5
                ORDER BY o.createdAt DESC
                LIMIT 1
            """)
    Optional<OtpCode> findLatestUsable(
            @Param("phone") String phone,
            @Param("purpose") OtpCode.Purpose purpose,
            @Param("now") OffsetDateTime now);

    @Query("""
                SELECT COUNT(o) FROM OtpCode o
                WHERE o.phone = :phone
                  AND o.purpose = :purpose
                  AND o.createdAt > :since
            """)
    long countRecentByPhone(
            @Param("phone") String phone,
            @Param("purpose") OtpCode.Purpose purpose,
            @Param("since") OffsetDateTime since);
}
