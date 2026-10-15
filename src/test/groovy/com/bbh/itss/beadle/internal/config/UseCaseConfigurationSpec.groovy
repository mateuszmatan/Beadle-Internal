package com.bbh.itss.beadle.internal.config

import com.bbh.itss.beadle.internal.application.UseCase
import com.bbh.itss.beadle.internal.support.RecordingTransactionManager
import org.springframework.aop.Advisor
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.interceptor.TransactionInterceptor
import spock.lang.Specification

import java.util.function.Supplier

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)

    def "the use case advisor puts a transaction interceptor around every @UseCase bean"() {
        expect:
        runner.run { ApplicationContext context ->
            def advisor = context.getBean('useCaseTransactionAdvisor', Advisor)
            assert advisor.advice instanceof TransactionInterceptor
            assert (advisor.advice as TransactionInterceptor).transactionAttributeSource instanceof UseCaseTransactionAttributeSource
            assert context.getBeansWithAnnotation(UseCase).isEmpty()
        }
    }
}
