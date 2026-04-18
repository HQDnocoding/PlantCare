package com.backend.community_service.repository;

import com.backend.community_service.entity.Vote;
import com.backend.community_service.entity.VoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, VoteId> {

    Optional<Vote> findByUserIdAndTargetIdAndTargetType(
            UUID userId, UUID targetId, Vote.TargetType targetType
    );

    // Lấy vote của user cho nhiều target cùng lúc (tránh N+1 khi render feed)
    @Query("""
        SELECT v FROM Vote v
        WHERE v.userId = :userId
          AND v.targetType = :targetType
          AND v.targetId IN :targetIds
        """)
    List<Vote> findByUserIdAndTargetTypeAndTargetIdIn(
            @Param("userId") UUID userId,
            @Param("targetType") Vote.TargetType targetType,
            @Param("targetIds") List<UUID> targetIds
    );
}
