package com.backend.community_service.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    long ttlSeconds() default 86400L;

    boolean useRequestBodyHash() default true;
}
