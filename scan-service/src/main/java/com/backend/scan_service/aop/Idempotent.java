package com.backend.scan_service.aop;

import java.lang.annotation.*;

/**
 * Annotation to mark methods requiring request idempotency.
 *
 * When applied to a handler method:
 * 1. Check if request hash already exists in DB
 * 2. If exists and valid: return cached response (422 if body conflict)
 * 3. If new: allow execution, then cache the response
 *
 * Usage:
 * 
 * @Idempotent(ttlSeconds = 3600)
 * @PostMapping
 *              public ResponseEntity<ScanResponse> save(...) { }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * TTL in seconds for cached response (default: 86400 = 24 hours)
     */
    long ttlSeconds() default 86400;
}
