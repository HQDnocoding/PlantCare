package com.backend.scan_service.services;

import com.backend.scan_service.dto.SaveScanRequest;
import com.backend.scan_service.dto.UpdateConvRequest;
import com.backend.scan_service.dto.ScanResponse;
import com.backend.scan_service.entity.ScanHistory;
import com.backend.scan_service.exception.AppException;
import com.backend.scan_service.exception.ErrorCode;
import com.backend.scan_service.repository.ScanHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ScanHistoryRepository repository;

    @Mock
    private FirebaseStorageService storageService;

    @InjectMocks
    private ScanService scanService;

    @Captor
    private ArgumentCaptor<ScanHistory> scanHistoryCaptor;

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("when image is null then throw INVALID_INPUT")
        void save_nullImage_throwsInvalidInput() {
            SaveScanRequest request = new SaveScanRequest();
            request.setImage(null);
            request.setDisease("Leaf spot");
            request.setConfidence(0.8);
            request.setConfidentEnough(true);

            assertThatThrownBy(() -> scanService.save(UUID.randomUUID(), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            verify(storageService, never()).uploadImage(any(MultipartFile.class), any(String.class));
            verify(repository, never()).save(any(ScanHistory.class));
        }

        @Test
        @DisplayName("when valid request then upload and save scan history")
        void save_validRequest_savesScanHistory() {
            UUID userId = UUID.randomUUID();
            MockMultipartFile image = new MockMultipartFile(
                    "image", "leaf.jpg", "image/jpeg", "dummy".getBytes());

            SaveScanRequest request = new SaveScanRequest();
            request.setImage(image);
            request.setDisease("Blight");
            request.setConfidence(0.92);
            request.setConfidentEnough(true);

            String expectedUrl = "https://example.com/scan.jpg";
            when(storageService.uploadImage(image, userId.toString())).thenReturn(expectedUrl);
            when(repository.save(any(ScanHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanResponse response = scanService.save(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getImageUrl()).isEqualTo(expectedUrl);
            assertThat(response.getDisease()).isEqualTo("Blight");
            assertThat(response.getConfidence()).isEqualTo(0.92);
            assertThat(response.getConfidentEnough()).isTrue();

            verify(repository, times(1)).save(scanHistoryCaptor.capture());
            ScanHistory saved = scanHistoryCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getImageUrl()).isEqualTo(expectedUrl);
            assertThat(saved.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.92));
            assertThat(saved.isConfidentEnough()).isTrue();
        }
    }

    @Nested
    @DisplayName("getHistory()")
    class GetHistoryTests {

        @Test
        @DisplayName("returns mapped scan response list")
        void getHistory_returnsScanResponses() {
            UUID userId = UUID.randomUUID();
            ScanHistory first = ScanHistory.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .imageUrl("url1")
                    .disease("Rust")
                    .confidence(BigDecimal.valueOf(0.7))
                    .confidentEnough(false)
                    .scannedAt(LocalDateTime.now().minusMinutes(5))
                    .build();
            ScanHistory second = ScanHistory.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .imageUrl("url2")
                    .disease("Mildew")
                    .confidence(BigDecimal.valueOf(0.95))
                    .confidentEnough(true)
                    .scannedAt(LocalDateTime.now())
                    .build();

            when(repository.findByUserIdOrderByScannedAtDesc(userId)).thenReturn(List.of(second, first));

            List<ScanResponse> responses = scanService.getHistory(userId);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getImageUrl()).isEqualTo("url2");
            assertThat(responses.get(1).getImageUrl()).isEqualTo("url1");
        }
    }

    @Nested
    @DisplayName("updateConv()")
    class UpdateConvTests {

        @Test
        @DisplayName("when scan exists then update convId")
        void updateConv_existingScan_updatesConvId() {
            UUID userId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();
            ScanHistory scan = ScanHistory.builder()
                    .id(scanId)
                    .userId(userId)
                    .imageUrl("url")
                    .disease("Spot")
                    .confidence(BigDecimal.valueOf(0.5))
                    .confidentEnough(false)
                    .build();

            UpdateConvRequest request = new UpdateConvRequest();
            request.setConvId("conv-123");

            when(repository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(scan));
            when(repository.save(any(ScanHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanResponse response = scanService.updateConv(userId, scanId, request);

            assertThat(response.getConvId()).isEqualTo("conv-123");
            verify(repository, times(1)).save(scan);
        }

        @Test
        @DisplayName("when scan not found then throw SCAN_NOT_FOUND")
        void updateConv_missingScan_throwsNotFound() {
            when(repository.findByIdAndUserId(any(UUID.class), any(UUID.class))).thenReturn(Optional.empty());

            UpdateConvRequest request = new UpdateConvRequest();
            request.setConvId("conv-123");

            assertThatThrownBy(() -> scanService.updateConv(UUID.randomUUID(), UUID.randomUUID(), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SCAN_NOT_FOUND);

            verify(repository, never()).save(any(ScanHistory.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("when scan exists then delete it")
        void delete_existingScan_deletesScan() {
            UUID userId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();
            ScanHistory scan = ScanHistory.builder()
                    .id(scanId)
                    .userId(userId)
                    .imageUrl("url")
                    .disease("Leaf Rust")
                    .confidence(BigDecimal.valueOf(0.1))
                    .confidentEnough(false)
                    .build();

            when(repository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(scan));

            scanService.delete(userId, scanId);

            verify(repository, times(1)).delete(scan);
        }

        @Test
        @DisplayName("when scan does not exist then throw SCAN_NOT_FOUND")
        void delete_missingScan_throwsNotFound() {
            when(repository.findByIdAndUserId(any(UUID.class), any(UUID.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scanService.delete(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SCAN_NOT_FOUND);

            verify(repository, never()).delete(any(ScanHistory.class));
        }
    }
}
