package com.backend.scan_service.services;

import com.google.firebase.cloud.StorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Slf4j
@Service
public class FirebaseStorageService {

    private static final String SCAN_FOLDER = "scans/";

    public String uploadImage(MultipartFile file, String userId) {
        try {
            String fileName = SCAN_FOLDER + userId + "/"
                    + UUID.randomUUID() + getExtension(file);

            StorageClient storageClient = StorageClient.getInstance();

            storageClient.bucket().create(
                    fileName,
                    file.getBytes(), // bytes của file
                    file.getContentType() // content type (image/jpeg, ...)
            );

            return String.format(
                    "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media",
                    storageClient.bucket().getName(),
                    fileName.replace("/", "%2F"));

        } catch (Exception e) {
            log.error("Failed to upload image to Firebase: {}", e.getMessage());
            throw new RuntimeException("Image upload failed", e);
        }
    }

    private String getExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            return original.substring(original.lastIndexOf("."));
        }
        return ".jpg";
    }
}