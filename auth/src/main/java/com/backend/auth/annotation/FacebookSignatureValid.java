package com.backend.auth.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FacebookSignatureValid {

    String signedRequestParamName() default "signed_request";

    boolean optional() default false;
}
