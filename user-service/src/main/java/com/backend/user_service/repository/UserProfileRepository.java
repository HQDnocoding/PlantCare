package com.backend.user_service.repository;

import com.backend.user_service.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);

    // Không lấy profile đã bị soft delete
    @Query("SELECT u FROM UserProfile u WHERE u.userId = :userId AND u.isDeleted = false")
    Optional<UserProfile> findActiveByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    // Batch load active profiles (không lấy deleted)
    @Query("SELECT u FROM UserProfile u WHERE u.userId IN :userIds AND u.isDeleted = false")
    List<UserProfile> findAllByUserIdIn(List<UUID> userIds);

}
