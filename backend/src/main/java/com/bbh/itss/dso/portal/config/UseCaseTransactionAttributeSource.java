package com.bbh.itss.dso.portal.config;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

import java.lang.reflect.Method;

import static org.springframework.aop.support.AopUtils.getMostSpecificMethod;
import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED;

class UseCaseTransactionAttributeSource implements TransactionAttributeSource {

    static final TransactionAttribute READ_WRITE = attribute(PROPAGATION_REQUIRED, false);
    static final TransactionAttribute READ_ONLY = attribute(PROPAGATION_REQUIRED, true);
    static final TransactionAttribute NONE = attribute(PROPAGATION_NOT_SUPPORTED, false);

    @Override
    public TransactionAttribute getTransactionAttribute(Method method, Class<?> targetClass) {
        if (method.getDeclaringClass() == Object.class) {
            return null;
        }
        Method implementation = targetClass == null ? method : getMostSpecificMethod(method, targetClass);
        MergedAnnotations annotations = MergedAnnotations.from(implementation, TYPE_HIERARCHY);
        if (annotations.isPresent(WithoutTransaction.class)) {
            return NONE;
        }
        return annotations.isPresent(ReadOnly.class) ? READ_ONLY : READ_WRITE;
    }

    private static TransactionAttribute attribute(int propagation, boolean readOnly) {
        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute(propagation);
        attribute.setReadOnly(readOnly);
        return attribute;
    }
}
