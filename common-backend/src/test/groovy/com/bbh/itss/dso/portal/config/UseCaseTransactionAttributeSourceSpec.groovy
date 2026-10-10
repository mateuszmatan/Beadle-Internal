package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.ReadOnly
import com.bbh.itss.dso.portal.application.WithoutTransaction
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.config.UseCaseTransactionAttributeSource.READ_ONLY
import static com.bbh.itss.dso.portal.config.UseCaseTransactionAttributeSource.READ_WRITE
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED

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
        declaring        | method         || propagation               | readOnly
        ReportingUseCase | 'record'       || PROPAGATION_REQUIRED      | false
        ReportingUseCase | 'audit'        || PROPAGATION_REQUIRED      | true
        ReportingUseCase | 'report'       || PROPAGATION_REQUIRED      | true
        ReportingPort    | 'report'       || PROPAGATION_REQUIRED      | true
        ReportingPort    | 'record'       || PROPAGATION_REQUIRED      | false
        ReportingUseCase | 'reportAgain'  || PROPAGATION_REQUIRED      | true
        ReportingUseCase | 'ask'          || PROPAGATION_NOT_SUPPORTED | false
        ReportingPort    | 'askAgain'     || PROPAGATION_NOT_SUPPORTED | false
        ReportingUseCase | 'askAgain'     || PROPAGATION_NOT_SUPPORTED | false
        ReportingUseCase | 'reportAndAsk' || PROPAGATION_NOT_SUPPORTED | false

        kind = propagation == PROPAGATION_NOT_SUPPORTED ? 'outside any transaction'
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
        READ_WRITE.rollbackOn(new IllegalStateException())
        READ_ONLY.rollbackOn(new IllegalArgumentException())
        !READ_WRITE.rollbackOn(new Exception())
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
