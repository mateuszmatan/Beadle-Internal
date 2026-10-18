package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.application.UseCase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.springframework.context.annotation.FilterType.ANNOTATION;

/**
 * The use cases are the {@link UseCase} classes of the application layer. They run without a transaction of their
 * own: each one calls a system, and the persistence adapters keep each write in a transaction of its own.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UseCase.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = ANNOTATION, classes = UseCase.class))
public class UseCaseConfiguration {
}
