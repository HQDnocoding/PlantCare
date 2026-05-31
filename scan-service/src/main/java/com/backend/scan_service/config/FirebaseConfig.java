package com.backend.scan_service.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    @Value("${firebase.storage-bucket}")
    private String storageBucket;

    /**
     * Load GoogleCredentials once and reuse across beans.
     */
    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        try (InputStream is = resolveCredentials()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is);

            if (credentials.getProjectId() == null) {
                throw new IllegalStateException("Invalid Firebase credentials: missing projectId");
            }

            log.info("GoogleCredentials loaded successfully | projectId={}", credentials.getProjectId());
            return credentials;
        }
    }

    /**
     * Initialize FirebaseApp as singleton.
     */
    @Bean
    public FirebaseApp firebaseApp(GoogleCredentials credentials) {

        // Prevent multiple initialization
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setStorageBucket(storageBucket)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);

        log.info("FirebaseApp initialized | bucket={}", storageBucket);
        return app;
    }

    /**
     * Google Cloud Storage client bean.
     */
    @Bean
    public Storage storage(GoogleCredentials credentials) {

        Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(credentials.getProjectId())
                .build()
                .getService();

        log.info("Google Cloud Storage initialized | projectId={}", credentials.getProjectId());
        return storage;
    }

    /**
     * Resolve credentials from filesystem or classpath.
     */
    private InputStream resolveCredentials() throws IOException {
        Path path = Paths.get(credentialsPath);

        // Load from external file (e.g., Docker, server)
        if (Files.exists(path)) {
            log.info("Loading Firebase credentials from filesystem: {}", credentialsPath);
            return Files.newInputStream(path);
        }

        // Load from classpath (e.g., resources folder inside JAR)
        log.info("Loading Firebase credentials from classpath: {}", credentialsPath);
        Resource resource = new ClassPathResource(credentialsPath);

        if (!resource.exists()) {
            throw new IllegalStateException("Firebase credentials not found: " + credentialsPath);
        }

        return resource.getInputStream();
    }
}