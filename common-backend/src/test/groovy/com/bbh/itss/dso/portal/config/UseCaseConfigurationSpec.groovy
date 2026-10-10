package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.UseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentUsagePort
import com.bbh.itss.dso.portal.domain.catalog.Department
import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage
import com.bbh.itss.dso.portal.support.RecordingTransactionManager
import org.springframework.aop.framework.Advised
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.interceptor.TransactionInterceptor
import spock.lang.Specification

import java.util.function.Supplier

import static com.bbh.itss.dso.portal.support.RecordingTransactionManager.transactionState
import static org.springframework.aop.support.AopUtils.isAopProxy

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()
    List<String> calls = []
    def usage = { long products -> [productCount: { -> products }] as DepartmentUsage }
    def departments = [findAll   : { -> calls << 'find all ' + transactionState(); [new Department(1L, 'AI Lab', 0)] },
                       findByName: { String name -> Optional.empty() },
                       save      : { Department department ->
                           calls << 'save ' + transactionState()
                           new Department(1L, department.name(), 0)
                       },
                       delete    : { long id -> calls << 'delete ' + transactionState() }] as DepartmentRepositoryPort
    def counts = [perDepartment: { -> [1L: usage(2)] }, unused: { -> usage(0) }] as DepartmentUsagePort

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(DepartmentRepositoryPort, { departments } as Supplier<DepartmentRepositoryPort>)
            .withBean(DepartmentUsagePort, { counts } as Supplier<DepartmentUsagePort>)

    def "every use case of the shared application layer is a bean behind a transactional proxy"() {
        expect:
        runner.run { ApplicationContext context ->
            def useCases = context.getBeansWithAnnotation(UseCase)
            assert useCases.keySet() == ['departmentService'] as Set
            useCases.values().each { useCase ->
                assert isAopProxy(useCase)
                assert (useCase as Advised).advisors*.advice.any { it instanceof TransactionInterceptor }
            }
        }
    }

    def "reading runs read-only and changing runs read-write"() {
        when:
        runner.run { ApplicationContext context ->
            context.getBean(DepartmentsUseCase).list()
            context.getBean(DepartmentsUseCase).create('Treasury')
        }

        then:
        transactions.log == ['begin read-only', 'commit', 'begin read-write', 'commit']
        calls == ['find all read-only', 'save read-write', 'find all read-write']
    }

    def "a refused change rolls the transaction back"() {
        when:
        runner.run { ApplicationContext context ->
            try {
                context.getBean(DepartmentsUseCase).delete(1L)
            } catch (IllegalStateException ignored) {
                calls << 'refused'
            }
        }

        then:
        transactions.log == ['begin read-write', 'rollback']
        calls == ['find all read-write', 'refused']
    }
}
