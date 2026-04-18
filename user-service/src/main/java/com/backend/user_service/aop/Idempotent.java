package com.backend.user_service.aop;

import java.lang.annotation.*;

/**
 * Marks a method as idempotent.
 * The IdempotencyAspect will intercept methods with this annotation
 * and ensure they process only once per unique idempotency key.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    long ttlSeconds() default 86400L;

    boolean useRequestBodyHash() default true;
}
