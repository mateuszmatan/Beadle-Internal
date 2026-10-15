package com.bbh.itss.beadle.internal.config

import com.bbh.itss.beadle.internal.application.UseCase
import com.bbh.itss.beadle.internal.application.calllog.CallLogService
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort
import com.bbh.itss.beadle.internal.application.confluence.ConfluenceService
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort
import com.bbh.itss.beadle.internal.application.cybertrack.CyberTrackService
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.jira.JiraService
import com.bbh.itss.beadle.internal.application.jira.port.in.JiraUseCase
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort
import com.bbh.itss.beadle.internal.application.qtest.QTestService
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort
import com.bbh.itss.beadle.internal.application.status.StatusService
import com.bbh.itss.beadle.internal.application.status.port.in.StatusUseCase
import com.bbh.itss.beadle.internal.support.RecordingTransactionManager
import org.springframework.aop.Advisor
import org.springframework.aop.support.AopUtils
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.interceptor.TransactionInterceptor
import spock.lang.Specification

import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static com.bbh.itss.beadle.internal.support.RecordingTransactionManager.transactionState

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()
    List<String> states = []

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(JiraPort, { [connected: { false }, versions: { String project ->
                states << transactionState()
                [version()]
            }] as JiraPort } as Supplier<JiraPort>)
            .withBean(CyberTrackPort, { [connected: { true }] as CyberTrackPort } as Supplier<CyberTrackPort>)
            .withBean(ConfluencePort, { [connected: { false }] as ConfluencePort } as Supplier<ConfluencePort>)
            .withBean(QTestPort, { [connected: { true }] as QTestPort } as Supplier<QTestPort>)
            .withBean(CallLogPort, { [:] as CallLogPort } as Supplier<CallLogPort>)

    def "the use case advisor puts a transaction interceptor around every @UseCase bean"() {
        expect:
        runner.run { ApplicationContext context ->
            def advisor = context.getBean('useCaseTransactionAdvisor', Advisor)
            assert advisor.advice instanceof TransactionInterceptor
            assert (advisor.advice as TransactionInterceptor).transactionAttributeSource instanceof UseCaseTransactionAttributeSource
            def useCases = context.getBeansWithAnnotation(UseCase).values()
            assert useCases.collect { AopUtils.getTargetClass(it) }.toSet() ==
                    [JiraService, CyberTrackService, ConfluenceService, QTestService, StatusService, CallLogService].toSet()
            assert useCases.every { AopUtils.isAopProxy(it) }
        }
    }

    def "the external calls of a use case run without a transaction"() {
        expect:
        runner.run { ApplicationContext context ->
            assert context.getBean(JiraUseCase).versions('CERT') == [version()]
            assert states == ['without transaction']
            assert transactions.log == []
            assert context.getBean(StatusUseCase).status().cyberTrack().mode() == 'live'
        }
    }
}
