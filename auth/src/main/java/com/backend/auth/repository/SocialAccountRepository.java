package com.backend.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.backend.auth.domain.entity.SocialAccount;

import feign.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
    @Modifying
    @Query("DELETE FROM SocialAccount s WHERE s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
