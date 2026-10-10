package com.bbh.itss.dso.portal.config;

import com.bbh.itss.dso.portal.application.UseCase;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.springframework.aop.support.annotation.AnnotationMatchingPointcut.forClassAnnotation;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;
import static org.springframework.context.annotation.FilterType.ANNOTATION;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UseCase.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = ANNOTATION, classes = UseCase.class))
public class UseCaseConfiguration {

    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    static Advisor useCaseTransactionAdvisor(BeanFactory beanFactory) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(new UseCaseTransactionAttributeSource());
        interceptor.setBeanFactory(beanFactory);
        return new DefaultPointcutAdvisor(forClassAnnotation(UseCase.class), interceptor);
    }
}
