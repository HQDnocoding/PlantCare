package com.backend.user_service.service;

import com.backend.user_service.exception.AppException;
import com.backend.user_service.exception.ErrorCode;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseStorageService {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024L; // 5MB

    @Value("${firebase.storage-bucket}")
    private String storageBucket;

    /**
     * Upload avatar lên Firebase Storage.
     * 
     * @return [avatarUrl, avatarPath] — path dùng để xóa file sau này
     */
    public String[] uploadAvatar(UUID userId, MultipartFile file) {
        validateImage(file);

        String ext = getExtension(file.getContentType());
        String path = "avatars/" + userId + "/" + UUID.randomUUID() + "." + ext;

        try {

            StorageClient.getInstance()
                    .bucket(storageBucket)
                    .create(path, file.getInputStream(), file.getContentType());

            // Public URL (bucket cần được set public read hoặc dùng signed URL)
            String url = String.format(
                    "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media",
                    storageBucket,
                    path.replace("/", "%2F"));

            log.info("Uploaded avatar for user {}: {}", userId, path);
            return new String[] { url, path };

        } catch (IOException e) {
            log.error("Failed to upload avatar for user {}", userId, e);
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    /**
     * Xóa file cũ khi user đổi avatar.
     */
    public void deleteFile(String path) {
        if (path == null || path.isBlank())
            return;
        try {
            StorageClient.getInstance()
                    .bucket(storageBucket)
                    .get(path)
                    .delete();
            log.info("Deleted file: {}", path);
        } catch (Exception e) {
            // Non-critical — log và bỏ qua
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new AppException(ErrorCode.INVALID_IMAGE_TYPE);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new AppException(ErrorCode.IMAGE_TOO_LARGE);
        }
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
