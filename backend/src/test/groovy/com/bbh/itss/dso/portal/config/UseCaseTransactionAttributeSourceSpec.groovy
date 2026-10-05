package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.ReadOnly
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
}
