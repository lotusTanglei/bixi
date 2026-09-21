package com.lotus.bixi.workflow.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Shared opt-in condition for workflow providers, consumers and transport adapters. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "workflow", name = "enabled", havingValue = "true")
public @interface ConditionalOnWorkflowEnabled {
}
