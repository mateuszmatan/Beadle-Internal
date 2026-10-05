package com.bbh.itss.dso.portal.config;

import com.bbh.itss.dso.portal.application.ReadOnly;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

import java.lang.reflect.Method;

class UseCaseTransactionAttributeSource implements TransactionAttributeSource {

    static final TransactionAttribute READ_WRITE = attribute(false);
    static final TransactionAttribute READ_ONLY = attribute(true);

    @Override
    public TransactionAttribute getTransactionAttribute(Method method, Class<?> targetClass) {
        if (method.getDeclaringClass() == Object.class) {
            return null;
        }
        Method implementation = targetClass == null ? method : AopUtils.getMostSpecificMethod(method, targetClass);
        return MergedAnnotations.from(implementation, SearchStrategy.TYPE_HIERARCHY).isPresent(ReadOnly.class)
                ? READ_ONLY : READ_WRITE;
    }

    private static TransactionAttribute attribute(boolean readOnly) {
        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRED);
        attribute.setReadOnly(readOnly);
        return attribute;
    }
}
