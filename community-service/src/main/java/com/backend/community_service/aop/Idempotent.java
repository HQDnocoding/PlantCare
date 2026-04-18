package com.backend.community_service.aop;

import java.lang.annotation.*;

/**
 * Marks a method as idempotent.
 * The IdempotencyAspect will intercept methods with this annotation
 * and ensure they process only once per unique idempotency key.
 *
 * Usage:
 * 
 * @Idempotent(ttlSeconds = 86400)
 *                        @PostMapping("/posts")
 *                        public ResponseEntity<PostDTO> createPost(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * TTL in seconds for the idempotency record (default: 24 hours)
     */
    long ttlSeconds() default 86400L;

    /**
     * Whether to use request body hash for conflict detection
     */
    boolean useRequestBodyHash() default true;
}
