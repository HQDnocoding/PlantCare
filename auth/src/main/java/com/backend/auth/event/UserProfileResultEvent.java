package com.backend.auth.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResultEvent {
    private UUID userId;
    private boolean success;
    private String error;
}
