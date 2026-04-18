package com.backend.auth.domain.dto.response;

import java.util.UUID;

public record InternalUserResponse(
                UUID id,
                String phone,
                String email,
                String role,
                boolean active) {
}