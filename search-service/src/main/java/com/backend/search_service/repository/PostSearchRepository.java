package com.backend.search_service.repository;

import com.backend.search_service.document.PostDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PostSearchRepository extends ElasticsearchRepository<PostDocument, String> {

  @Query("""
      {
        "bool": {
          "should": [
            {
              "wildcard": {
                "content": {
                  "value": "*?0*",
                  "case_insensitive": true
                }
              }
            },
            {
              "wildcard": {
                "authorName": {
                  "value": "*?0*",
                  "case_insensitive": true
                }
              }
            },
            {
              "terms": {
                "tags": ["?0"]
              }
            }
          ],
          "minimum_should_match": 1
        }
      }
      """)
  Page<PostDocument> searchByKeyword(String keyword, Pageable pageable);
}