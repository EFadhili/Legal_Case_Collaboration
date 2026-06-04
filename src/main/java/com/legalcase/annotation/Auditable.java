package com.legalcase.annotation;

import com.legalcase.enums.AuditAction;
import com.legalcase.enums.EntityType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    AuditAction action();
    EntityType entityType();
    boolean captureBefore() default true;  // For updates, capture state before change
    boolean captureAfter() default true;   // For updates, capture state after change
}