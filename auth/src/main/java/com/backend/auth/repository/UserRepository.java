package com.backend.auth.repository;

import com.backend.auth.domain.entity.SocialAccount;
import com.backend.auth.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository <User, UUID>{
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    boolean existsByPhoneAndDeletedAtIsNull(String phone);
    boolean existsByEmailAndDeletedAtIsNull(String email);

    @Query("""
        SELECT u FROM User u
        JOIN u.socialAccounts sa
        WHERE sa.provider = :provider
          AND sa.providerId = :providerId
          AND u.deletedAt IS NULL
    """)
    Optional<User> findBySocialAccount(
            @Param("provider") SocialAccount.Provider provider,
            @Param("providerId") String providerId
    );
}
