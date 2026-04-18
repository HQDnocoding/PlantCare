package com.backend.auth;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// @Testcontainers: JUnit 5 extension that manages container lifecycle
// @SpringBootTest: starts full Spring context
// @ActiveProfiles("test"): loads application-test.yml
// @Transactional: each test method rolls back DB after running
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    // @Container + static = one container shared across ALL test classes.
    // Testcontainers reuses the same container instead of starting a new one
    // for each test class — saves 5-10 seconds per test class.
    //
    // @ServiceConnection: Spring Boot 3.1+ automatically reads the container's
    // dynamic host/port and injects them into spring.datasource.url.
    // No need to manually override datasource URL in application-test.yml.
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("agri_auth_test")
                    .withUsername("test_user")
                    .withPassword("test_pass")
                    // Reuse container across test runs in local dev (speeds up feedback loop).
                    // In CI, this is ignored — a fresh container is always created.
                    .withReuse(true);
}
