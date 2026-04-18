package com.backend.user_service.service;

import com.backend.user_service.dto.UserStatsResponse;
import com.backend.user_service.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatsService {

    private final UserProfileRepository userProfileRepository;

    public UserStatsResponse getUserStats() {
        try {
            long totalUsers = userProfileRepository.count();

            // Đơn giản: active users = users có createdAt trong 24h gần nhất
            long activeUsersToday = getActiveUsersToday();

            // New users in this week
            long newUsersThisWeek = getNewUsersThisWeek();

            return UserStatsResponse.builder()
                    .totalUsers(totalUsers)
                    .activeUsersToday(activeUsersToday)
                    .newUsersThisWeek(newUsersThisWeek)
                    .maleCount(0) // Có thể mở rộng nếu có field gender
                    .femaleCount(0) // Có thể mở rộng nếu có field gender
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception ex) {
            log.error("Error fetching user stats", ex);
            return UserStatsResponse.builder()
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    private long getActiveUsersToday() {
        // TODO: Implement logic để lấy users active trong 24h
        // Có thể sử dụng cache, Redis, hoặc database query
        // Tạm thời return 0
        return 0;
    }

    private long getNewUsersThisWeek() {
        // TODO: Implement logic để lấy users mới trong tuần
        // Query database với createdAt >= now - 7 days
        // Tạm thời return 0
        return 0;
    }
}
