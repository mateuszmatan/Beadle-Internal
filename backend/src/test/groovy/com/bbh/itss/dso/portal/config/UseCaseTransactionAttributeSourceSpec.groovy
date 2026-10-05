package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.ReadOnly
import com.bbh.itss.dso.portal.application.WithoutTransaction
import org.springframework.transaction.TransactionDefinition
import spock.lang.Specification
import spock.lang.Subject

class UseCaseTransactionAttributeSourceSpec extends Specification {

    @Subject
    def source = new UseCaseTransactionAttributeSource()

    def "#method of #declaring.simpleName runs #kind"() {
        when:
        def attribute = source.getTransactionAttribute(declaring.getMethod(method), ReportingUseCase)

        then:
        attribute.propagationBehavior == propagation
        attribute.readOnly == readOnly

        where:
        declaring        | method         || propagation                                     | readOnly
        ReportingUseCase | 'record'       || TransactionDefinition.PROPAGATION_REQUIRED      | false
        ReportingUseCase | 'audit'        || TransactionDefinition.PROPAGATION_REQUIRED      | true
        ReportingUseCase | 'report'       || TransactionDefinition.PROPAGATION_REQUIRED      | true
        ReportingPort    | 'report'       || TransactionDefinition.PROPAGATION_REQUIRED      | true
        ReportingPort    | 'record'       || TransactionDefinition.PROPAGATION_REQUIRED      | false
        ReportingUseCase | 'reportAgain'  || TransactionDefinition.PROPAGATION_REQUIRED      | true
        ReportingUseCase | 'ask'          || TransactionDefinition.PROPAGATION_NOT_SUPPORTED | false
        ReportingPort    | 'askAgain'     || TransactionDefinition.PROPAGATION_NOT_SUPPORTED | false
        ReportingUseCase | 'askAgain'     || TransactionDefinition.PROPAGATION_NOT_SUPPORTED | false
        ReportingUseCase | 'reportAndAsk' || TransactionDefinition.PROPAGATION_NOT_SUPPORTED | false

        kind = propagation == TransactionDefinition.PROPAGATION_NOT_SUPPORTED ? 'outside any transaction'
                : readOnly ? 'in a read-only transaction' : 'in a read-write transaction'
    }

    def "a method is looked at with the methods it overrides when the target class is not known"() {
        expect:
        source.getTransactionAttribute(ReportingPort.getMethod('report'), null).readOnly
        source.getTransactionAttribute(ReportingUseCase.getMethod('report'), null).readOnly
        !source.getTransactionAttribute(ReportingUseCase.getMethod('record'), null).readOnly
    }

    def "the methods every object has run without a transaction"() {
        expect:
        source.getTransactionAttribute(Object.getMethod('toString'), ReportingUseCase) == null
        source.getTransactionAttribute(Object.getMethod('hashCode'), ReportingUseCase) == null
    }

    def "domain exceptions and every other runtime failure roll the transaction back"() {
        expect:
        UseCaseTransactionAttributeSource.READ_WRITE.rollbackOn(new IllegalStateException())
        UseCaseTransactionAttributeSource.READ_ONLY.rollbackOn(new IllegalArgumentException())
        !UseCaseTransactionAttributeSource.READ_WRITE.rollbackOn(new Exception())
    }
}

interface ReportingPort {

    @ReadOnly
    String report()

    String record()

    @WithoutTransaction
    String askAgain()
}

interface ReportingAgainPort {

    @ReadOnly
    String reportAgain()
}

class ReportingUseCase implements ReportingPort, ReportingAgainPort {

    String report() {
        'report'
    }

    String reportAgain() {
        'again'
    }

    String record() {
        'record'
    }

    @ReadOnly
    String audit() {
        'audit'
    }

    @WithoutTransaction
    String ask() {
        'ask'
    }

    String askAgain() {
        'ask again'
    }

    @ReadOnly
    @WithoutTransaction
    String reportAndAsk() {
        'both'
    }
}
