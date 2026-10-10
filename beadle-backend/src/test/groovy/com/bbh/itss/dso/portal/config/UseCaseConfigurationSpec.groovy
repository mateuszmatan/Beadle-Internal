package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.UseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentUsagePort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.CyberTrackPort
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort
import com.bbh.itss.dso.portal.application.change.port.out.ProTechLookupPort
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort
import com.bbh.itss.dso.portal.application.user.port.out.SignedInUserPort
import com.bbh.itss.dso.portal.domain.catalog.Department
import com.bbh.itss.dso.portal.domain.catalog.Product
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

import java.time.Clock
import java.util.function.Supplier

import static com.bbh.itss.dso.portal.support.RecordingTransactionManager.transactionState
import static java.time.Clock.systemUTC
import static org.springframework.aop.support.AopUtils.isAopProxy

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()
    List<String> calls = []
    ProductRepositoryPort products = Mock()
    DepartmentRepositoryPort departments = Mock()
    JiraPort jira = Mock()
    ServiceNowPort serviceNow = Mock()
    CyberTrackPort cyberTrack = Mock()

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(ProductRepositoryPort, { products } as Supplier<ProductRepositoryPort>)
            .withBean(DepartmentRepositoryPort, { departments } as Supplier<DepartmentRepositoryPort>)
            .withBean(DepartmentUsagePort, { Mock(DepartmentUsagePort) } as Supplier<DepartmentUsagePort>)
            .withBean(ChangeProductsPort, { Mock(ChangeProductsPort) } as Supplier<ChangeProductsPort>)
            .withBean(ChangeProfileRepositoryPort, { Mock(ChangeProfileRepositoryPort) } as Supplier<ChangeProfileRepositoryPort>)
            .withBean(ProductionChangeRepositoryPort, { Mock(ProductionChangeRepositoryPort) } as Supplier<ProductionChangeRepositoryPort>)
            .withBean(JiraPort, { jira } as Supplier<JiraPort>)
            .withBean(ServiceNowPort, { serviceNow } as Supplier<ServiceNowPort>)
            .withBean(CyberTrackPort, { cyberTrack } as Supplier<CyberTrackPort>)
            .withBean(ProTechLookupPort, { Mock(ProTechLookupPort) } as Supplier<ProTechLookupPort>)
            .withBean(SignedInUserPort, { { -> 'Mateusz Matan' } as SignedInUserPort } as Supplier<SignedInUserPort>)
            .withBean(Clock, { systemUTC() } as Supplier<Clock>)

    def "every use case of Beadle is a bean behind a transactional proxy"() {
        expect:
        runner.run { ApplicationContext context ->
            def useCases = context.getBeansWithAnnotation(UseCase)
            assert useCases.keySet() == ['departmentService', 'productService', 'changeProfileService',
                                         'productionChangeService', 'changeOptionsService', 'lookupService',
                                         'signedInUserService'] as Set
            useCases.values().each { useCase ->
                assert isAopProxy(useCase)
                assert (useCase as Advised).advisors*.advice.any { it instanceof TransactionInterceptor }
            }
        }
    }

    def "products are listed in a read-only transaction and created in a read-write one"() {
        given:
        departments.findAll() >> { calls << 'departments ' + transactionState(); [new Department(3L, 'Custody', 0)] }
        products.findAll() >> { calls << 'products ' + transactionState(); [] }
        products.departmentExists(3L) >> true
        products.findProductByCode(_) >> Optional.empty()
        products.findProductByName(_) >> Optional.empty()
        products.save(_ as Product) >> { Product product ->
            calls << 'save ' + transactionState()
            new Product(7L, product.details(), 0, null)
        }

        when:
        runner.run { ApplicationContext context ->
            context.getBean(ProductsUseCase).list(null)
            context.getBean(ProductsUseCase).create(new ProductCommand('CERT', 'CertScanner', 3L, null, null, null))
        }

        then:
        transactions.log == ['begin read-only', 'commit', 'begin read-write', 'commit']
        calls == ['departments read-only', 'products read-only', 'save read-write', 'departments read-write']
    }

    def "Beadle asks Jira, ProTech and CyberTrack whether they are connected with no transaction"() {
        given:
        ChangeIntegrations integrations = null
        jira.connected() >> { calls << 'Jira ' + transactionState(); true }
        serviceNow.connected() >> { calls << 'ProTech ' + transactionState(); false }
        cyberTrack.connected() >> { calls << 'CyberTrack ' + transactionState(); true }

        when:
        runner.run { ApplicationContext context ->
            integrations = context.getBean(ProductionChangesUseCase).integrations()
        }

        then:
        integrations == new ChangeIntegrations(true, false, true)
        calls == ['Jira without transaction', 'ProTech without transaction', 'CyberTrack without transaction']
        transactions.log == []
    }
}
