package com.backend.community_service.service;

import com.backend.community_service.exception.AppException;
import com.backend.community_service.exception.ErrorCode;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseStorageService {

    private final Storage storage;

    @Value("${firebase.storage-bucket}")
    private String bucketName;

    @Value("${app.upload.max-file-size:5242880}") // 5MB default
    private long maxFileSize;

    private static final String[] ALLOWED_MIME_TYPES = { "image/jpeg", "image/png" };
    private static final String[] ALLOWED_EXTENSIONS = { ".jpg", ".jpeg", ".png" };

    /**
     * Upload ảnh post lên Firebase Storage
     * 
     * @param authorId  ID của người đăng
     * @param imageFile File ảnh cần upload
     * @return Array chứa [publicUrl, storagePath]
     */
    public String[] uploadPostImage(UUID authorId, MultipartFile imageFile) {
        validateImageFile(imageFile);
        String publicUrl = uploadFile("posts", authorId, imageFile);
        return new String[] { publicUrl, extractStoragePath(publicUrl) };
    }

    private String uploadFile(String folder, UUID ownerId, MultipartFile file) {
        try {
            // 1. Kiểm tra file trống
            if (file.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_IMAGE_TYPE);
            }

            // 2. Tạo đường dẫn lưu trữ: folder/ownerId/filename
            String fileName = generateFileName(file.getOriginalFilename());
            String storagePath = String.format("%s/%s/%s", folder, ownerId, fileName);

            BlobId blobId = BlobId.of(bucketName, storagePath);

            // 3. Metadata quan trọng để trình duyệt/mobile hiển thị đúng thay vì tải về
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    // Thêm metadata để Firebase Storage nhận diện đây là file public media
                    .setMetadata(
                            Collections.singletonMap("firebaseStorageDownloadTokens", UUID.randomUUID().toString()))
                    .build();

            storage.create(blobInfo, file.getBytes());

            log.info("File uploaded successfully | path={}", storagePath);
            return generatePublicUrl(storagePath);

        } catch (IOException e) {
            log.error("Cloud storage upload failed for owner={}", ownerId, e);
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    /**
     * Upload ảnh avatar lên Firebase Storage
     * 
     * @param userId    ID của user
     * @param imageFile File ảnh cần upload
     * @return Public URL của ảnh
     */
    public String uploadUserAvatar(UUID userId, MultipartFile imageFile) {
        validateImageFile(imageFile);
        return uploadFile("avatars", userId, imageFile);
    }

    /**
     * Xóa file từ Firebase Storage
     * 
     * @param storagePath Đường dẫn của file trong storage
     */
    public void deleteFile(String storagePath) {
        try {
            BlobId blobId = BlobId.of(bucketName, storagePath);
            boolean deleted = storage.delete(blobId);
            if (deleted) {
                log.info("File deleted | path={}", storagePath);
            } else {
                log.warn("File not found for deletion | path={}", storagePath);
            }
        } catch (Exception e) {
            log.error("Failed to delete file | path={}", storagePath, e);
        }
    }

    /**
     * Kiểm tra file có tồn tại hay không
     */
    public boolean fileExists(String storagePath) {
        BlobId blobId = BlobId.of(bucketName, storagePath);
        Blob blob = storage.get(blobId);
        return blob != null && blob.exists();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        // Validate file type
        String contentType = file.getContentType();
        boolean isValidType = false;
        if (contentType != null) {
            for (String allowed : ALLOWED_MIME_TYPES) {
                if (contentType.equalsIgnoreCase(allowed)) {
                    isValidType = true;
                    break;
                }
            }
        }
        if (!isValidType) {
            throw new AppException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        // Validate file extension
        String originalFileName = file.getOriginalFilename();
        if (originalFileName != null) {
            String ext = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
            boolean isValidExt = false;
            for (String allowed : ALLOWED_EXTENSIONS) {
                if (ext.equals(allowed)) {
                    isValidExt = true;
                    break;
                }
            }
            if (!isValidExt) {
                throw new AppException(ErrorCode.INVALID_IMAGE_TYPE);
            }
        }

        // Validate file size
        if (file.getSize() > maxFileSize) {
            throw new AppException(ErrorCode.IMAGE_TOO_LARGE);
        }
    }

    private String generateFileName(String originalFileName) {
        String ext = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            ext = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + ext;
    }

    private String generatePublicUrl(String storagePath) {
        return String.format("https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media",
                bucketName,
                storagePath.replace("/", "%2F"));
    }

    private String extractStoragePath(String publicUrl) {
        return publicUrl.split("/o/")[1].split("\\?")[0].replace("%2F", "/");
    }
}
