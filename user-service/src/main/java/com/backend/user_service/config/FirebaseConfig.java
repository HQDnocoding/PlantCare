package com.backend.user_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    @Value("${firebase.storage-bucket}")
    private String storageBucket;

    @PostConstruct
    public void init() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized");
            return;
        }

        InputStream credentialsStream = resolveCredentials();

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                .setStorageBucket(storageBucket)
                .build();

        FirebaseApp.initializeApp(options);
        log.info("FirebaseApp initialized | bucket={}", storageBucket);
    }

    private InputStream resolveCredentials() throws IOException {
        if (Files.exists(Paths.get(credentialsPath))) {
            log.info("Loading Firebase credentials from path: {}", credentialsPath);
            return new FileInputStream(credentialsPath);
        }

        log.info("Loading Firebase credentials from classpath: {}", credentialsPath);
        return new ClassPathResource(credentialsPath).getInputStream();
    }
}
