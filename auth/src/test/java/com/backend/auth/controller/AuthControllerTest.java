package com.backend.auth.controller;

import com.backend.auth.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Extends AbstractIntegrationTest which handles:
//   - @SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test") + @Transactional
//   - Shared PostgreSQL container via Testcontainers
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // -----------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/guest")
    class GuestLoginTests {

        @Test
        @DisplayName("Guest login - should return accessToken")
        void guestLogin_returnsAccessToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/guest")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
    }
}
