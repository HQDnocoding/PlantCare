package com.backend.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {

    @JsonProperty("total_users")
    private long totalUsers;

    @JsonProperty("active_users_today")
    private long activeUsersToday;

    @JsonProperty("new_users_this_week")
    private long newUsersThisWeek;

    @JsonProperty("male_count")
    private long maleCount;

    @JsonProperty("female_count")
    private long femaleCount;

    @JsonProperty("timestamp")
    private long timestamp;
}
