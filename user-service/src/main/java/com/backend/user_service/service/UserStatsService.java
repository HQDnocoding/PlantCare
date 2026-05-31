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

            long activeUsersToday = getActiveUsersToday();

            long newUsersThisWeek = getNewUsersThisWeek();

            return UserStatsResponse.builder()
                    .totalUsers(totalUsers)
                    .activeUsersToday(activeUsersToday)
                    .newUsersThisWeek(newUsersThisWeek)
                    .maleCount(0)
                    .femaleCount(0)
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

        return 0;
    }

    private long getNewUsersThisWeek() {

        return 0;
    }
}
