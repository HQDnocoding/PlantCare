package com.backend.search_service.repository;

import com.backend.search_service.document.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {

    @Query("""
        {
          "multi_match": {
            "query": "?0",
            "fields": ["displayName^2", "bio^1"],
            "fuzziness": "AUTO"
          }
        }
        """)
    Page<UserDocument> searchByKeyword(String keyword, Pageable pageable);
}
