package com.backend.search_service.service;

import com.backend.search_service.constants.CacheNames;
import com.backend.search_service.dto.SearchRequest;
import com.backend.search_service.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchCacheService {

    private final SearchService searchService;

    @Cacheable(value = "search-result", key = "#request.type + ':' + #request.q + ':' + #page")
    public SearchResponse searchWithCache(SearchRequest request, int page) {
        log.debug("Cache miss for search: type={}, q={}, page={}", request.getType(), request.getQ(), page);
        return searchService.search(request, page);
    }

    @CacheEvict(value = "search-result", allEntries = true)
    public void invalidateSearchCache() {
        log.info("Invalidated all search result cache");
    }

    @CacheEvict(value = CacheNames.AUTHOR, allEntries = true)
    public void invalidateAuthorCache() {
        log.info("Invalidated all author cache");
    }

    @CacheEvict(value = CacheNames.AUTHOR, key = "#userId")
    public void invalidateAuthorCache(UUID userId) {
        log.debug("Invalidated author cache for userId={}", userId);
    }
}
