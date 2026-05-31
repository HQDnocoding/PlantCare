package com.backend.scan_service.services;

import com.backend.scan_service.constants.CacheNames;
import com.backend.scan_service.dto.SaveScanRequest;
import com.backend.scan_service.dto.ScanResponse;
import com.backend.scan_service.dto.UpdateConvRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanCacheService {

    private final ScanService scanService;

    @Cacheable(value = "scan-result", key = "#userId")
    public List<ScanResponse> getHistoryWithCache(UUID userId) {
        log.debug("Cache miss for scan history: userId={}", userId);
        return scanService.getHistory(userId);
    }

    @CacheEvict(value = "scan-result", key = "#userId")
    public ScanResponse saveWithCacheInvalidation(UUID userId, SaveScanRequest req) {
        log.debug("Saving scan and invalidating cache for userId={}", userId);
        return scanService.save(userId, req);
    }

    @CacheEvict(value = "scan-result", key = "#userId")
    public void deleteWithCacheInvalidation(UUID userId, UUID scanId) {
        log.debug("Deleting scan and invalidating cache for userId={}", userId);
        scanService.delete(userId, scanId);
    }

    @CacheEvict(value = "scan-result", key = "#userId")
    public ScanResponse updateConvWithCacheInvalidation(UUID userId, UUID scanId, UpdateConvRequest req) {
        log.debug("Updating scan conv and invalidating cache for userId={}", userId);
        return scanService.updateConv(userId, scanId, req);
    }

    @CacheEvict(value = CacheNames.AUTHOR, allEntries = true)
    public void invalidateAuthorCache() {
        log.info("Invalidated all author cache");
    }
}
