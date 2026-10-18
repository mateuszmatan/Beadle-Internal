package com.bbh.itss.beadle.internal.adapter.out.http;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Creates the adapter of a system only when {@code beadle.<system>.url} is set ({@code set = true}), or, for its demo,
 * only when it is blank ({@code set = false}).
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
@Conditional(ConditionalOnSystemUrl.OnSystemUrl.class)
public @interface ConditionalOnSystemUrl {

    String value();

    boolean set() default true;

    class OnSystemUrl implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var attributes = metadata.getAnnotationAttributes(ConditionalOnSystemUrl.class.getName());
            String url = context.getEnvironment().getProperty("beadle." + attributes.get("value") + ".url");
            return isNotBlank(url) == (boolean) attributes.get("set");
        }
    }
}
