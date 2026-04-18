package com.backend.community_service.entity;

import java.io.Serializable;
import java.util.UUID;

public class VoteId implements Serializable {
    private UUID userId;
    private UUID targetId;
    private Vote.TargetType targetType;

    // equals + hashCode bắt buộc cho composite key
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoteId other)) return false;
        return java.util.Objects.equals(userId, other.userId)
                && java.util.Objects.equals(targetId, other.targetId)
                && targetType == other.targetType;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(userId, targetId, targetType);
    }
}