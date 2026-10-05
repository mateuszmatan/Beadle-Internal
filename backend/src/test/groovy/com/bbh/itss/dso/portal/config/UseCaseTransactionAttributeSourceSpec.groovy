package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.ReadOnly
import com.bbh.itss.dso.portal.application.WithoutTransaction
import org.springframework.transaction.TransactionDefinition
import spock.lang.Specification
import spock.lang.Subject

class UseCaseTransactionAttributeSourceSpec extends Specification {

    @Subject
    def source = new UseCaseTransactionAttributeSource()

    def "#method of the use case runs in a #kind transaction"() {
        when:
        def attribute = source.getTransactionAttribute(declaring.getMethod(method), ReportingUseCase)

        then:
        attribute.propagationBehavior == TransactionDefinition.PROPAGATION_REQUIRED
        attribute.readOnly == readOnly

        where:
        declaring        | method          || readOnly
        ReportingUseCase | 'record'        || false
        ReportingUseCase | 'audit'         || true
        ReportingUseCase | 'report'        || true
        ReportingPort    | 'report'        || true
        ReportingPort    | 'record'        || false
        ReportingUseCase | 'reportAgain'   || true

        kind = readOnly ? 'read-only' : 'read-write'
    }

    def "#method runs outside any transaction, so nothing it waits for holds a database connection"() {
        when:
        def attribute = source.getTransactionAttribute(declaring.getMethod(method), ReportingUseCase)

        then:
        attribute.propagationBehavior == TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        !attribute.readOnly

        where:
        declaring        | method
        ReportingUseCase | 'ask'
        ReportingPort    | 'askAgain'
        ReportingUseCase | 'askAgain'
    }

    def "a method is looked at with the methods it overrides when the target class is not known"() {
        expect:
        source.getTransactionAttribute(ReportingPort.getMethod('report'), null).readOnly
        source.getTransactionAttribute(ReportingUseCase.getMethod('report'), null).readOnly
        !source.getTransactionAttribute(ReportingUseCase.getMethod('record'), null).readOnly
    }

    def "a method that both reads and asks another system is not run in a transaction"() {
        expect:
        source.getTransactionAttribute(ReportingUseCase.getMethod('reportAndAsk'), ReportingUseCase)
                .propagationBehavior == TransactionDefinition.PROPAGATION_NOT_SUPPORTED
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
