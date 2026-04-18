package com.backend.user_service.repository;

import com.backend.user_service.entity.Follow;
import com.backend.user_service.entity.FollowId;
import com.backend.user_service.entity.UserProfile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :followerId AND f.followingId = :followingId")
    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    long countByFollowingId(UUID followingId); // follower count

    long countByFollowerId(UUID followerId); // following count

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId")
    List<UUID> findFollowingIdsByFollowerId(@Param("followerId") UUID followerId);

    @Query("SELECT up FROM UserProfile up JOIN Follow f ON up.userId = f.followerId WHERE f.followingId = :userId")
    List<UserProfile> findFollowersProfiles(@Param("userId") UUID userId);

    @Query("SELECT up FROM UserProfile up " +
            "JOIN Follow f ON up.userId = f.followerId " +
            "WHERE f.followingId = :myId AND up.isDeleted = false")
    List<UserProfile> findFollowerProfiles(@Param("myId") UUID myId);

    // Lấy profile của những người MÌNH ĐANG FOLLOW
    @Query("SELECT up FROM UserProfile up " +
            "JOIN Follow f ON up.userId = f.followingId " +
            "WHERE f.followerId = :myId AND up.isDeleted = false")
    List<UserProfile> findFollowingProfiles(@Param("myId") UUID myId);
}
