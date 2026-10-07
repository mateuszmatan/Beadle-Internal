package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.UseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase
import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase
import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.monitoring.LatestRuns
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.settings.Scanner
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.support.Fixtures
import org.springframework.aop.framework.Advised
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues.bbhDefaults
import static com.bbh.itss.dso.portal.domain.settings.Scanner.SAST
import static com.bbh.itss.dso.portal.support.Fixtures.copy
import static java.time.Clock.systemUTC
import static java.time.Instant.EPOCH
import static org.springframework.aop.support.AopUtils.isAopProxy
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive
import static org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()
    List<String> calls = []
    GlobalSettings stored = new GlobalSettings(bbhDefaults(), 1, Instant.parse('2026-10-04T12:00:00Z'))
    def repository = [load: { -> calls << 'load ' + transactionState(); Optional.ofNullable(stored) },
                      save: { GlobalSettings settings ->
                          calls << 'save ' + transactionState()
                          stored = new GlobalSettings(settings.values(), settings.version() + 1, EPOCH)
                      }] as GlobalSettingsRepositoryPort
    def bbh = bbhDefaults()
    ProductRepositoryPort products = Mock()
    DepartmentRepositoryPort departments = Mock()
    PipelineCountsPort pipelineCounts = Mock()
    PipelineRepositoryPort pipelines = Mock()
    PipelineRunsPort runs = Mock()
    DashboardLinksPort dashboards = Mock()
    RunEvidencePort evidence = Mock()
    ChangeProfileRepositoryPort changeProfiles = Mock()
    ProductionChangeRepositoryPort productionChanges = Mock()
    JiraPort jira = Mock()
    ServiceNowPort serviceNow = Mock()

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(GlobalSettingsRepositoryPort, { repository } as Supplier<GlobalSettingsRepositoryPort>)
            .withBean(ProductRepositoryPort, { products } as Supplier<ProductRepositoryPort>)
            .withBean(DepartmentRepositoryPort, { departments } as Supplier<DepartmentRepositoryPort>)
            .withBean(PipelineCountsPort, { pipelineCounts } as Supplier<PipelineCountsPort>)
            .withBean(PipelineRepositoryPort, { pipelines } as Supplier<PipelineRepositoryPort>)
            .withBean(PipelineRunsPort, { runs } as Supplier<PipelineRunsPort>)
            .withBean(DashboardLinksPort, { dashboards } as Supplier<DashboardLinksPort>)
            .withBean(RunEvidencePort, { evidence } as Supplier<RunEvidencePort>)
            .withBean(ChangeProfileRepositoryPort, { changeProfiles } as Supplier<ChangeProfileRepositoryPort>)
            .withBean(ProductionChangeRepositoryPort, { productionChanges } as Supplier<ProductionChangeRepositoryPort>)
            .withBean(JiraPort, { jira } as Supplier<JiraPort>)
            .withBean(ServiceNowPort, { serviceNow } as Supplier<ServiceNowPort>)
            .withBean(KeyGenerator, { { -> 'key' } as KeyGenerator } as Supplier<KeyGenerator>)
            .withBean(Clock, { systemUTC() } as Supplier<Clock>)

    def "every use case of the application layer is a bean behind a transactional proxy"() {
        expect:
        runner.run { ApplicationContext context ->
            def useCases = context.getBeansWithAnnotation(UseCase)
            assert useCases.keySet().containsAll(['globalSettingsService', 'productCatalogService', 'departmentService',
                                                  'pipelineService', 'pipelineConfigService',
                                                  'pipelineMonitoringService', 'changeEvidenceService',
                                                  'monitoringTargetsService', 'changeProfileService',
                                                  'productionChangeService'])
            useCases.values().each { useCase ->
                assert isAopProxy(useCase)
                assert (useCase as Advised).advisors*.advice.any { it instanceof TransactionInterceptor }
            }
        }
    }

    def "reading the settings runs read-only and creating them at start-up runs read-write"() {
        given:
        stored = existing

        when:
        runner.run { ApplicationContext context -> context.getBean(ManageGlobalSettingsUseCase)."$method"() }

        then:
        transactions.log == ['begin ' + mode, 'commit']
        calls == expected

        where:
        method         | existing                     || mode        | expected
        'current'      | GlobalSettings.bbhDefaults() || 'read-only'  | ['load read-only']
        'ensureExists' | null                         || 'read-write' | ['load read-write', 'save read-write']
    }

    def "a change is saved in one read-write transaction"() {
        when:
        runner.run { ApplicationContext context ->
            context.getBean(ManageGlobalSettingsUseCase).update(1L,
                    bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com')))
        }

        then:
        transactions.log == ['begin read-write', 'commit']
        calls == ['load read-write', 'save read-write']
        stored.jenkinsUrl() == 'https://jenkins.bbh.com'
    }

    def "a domain exception rolls the transaction back: #reason"() {
        given:
        Throwable failure = null

        when:
        runner.run { ApplicationContext context ->
            try {
                context.getBean(ManageGlobalSettingsUseCase).update(version, values)
            } catch (RuntimeException e) {
                failure = e
            }
        }

        then:
        exception.isInstance(failure)
        transactions.log == ['begin read-write', 'rollback']
        calls == ['load read-write']

        where:
        reason                   | version | values             || exception
        'a concurrent change'    | 0L      | bbhDefaults()      || IllegalStateException
        'a broken business rule' | 1L      | withoutLimit(SAST) || InvalidRequestException
    }

    def "the monitoring and evidence pages query InfluxDB with no transaction and no database connection of their own"() {
        given:
        def influx = { String query, Object answer -> calls << query + ' ' + transactionState(); answer }
        runs.configured() >> true
        runs.ping() >> { influx('ping', null) }
        runs.latestRuns(*_) >> { influx('latest runs', LatestRuns.none()) }
        runs.recentRuns(*_) >> { influx('recent runs', []) }
        runs.doraPoints(*_) >> { influx('DORA points', [:]) }
        evidence.evidenceOf(_) >> { influx('evidence', [:]) }
        dashboards.url() >> Optional.empty()
        dashboards.dashboardUrl(*_) >> Optional.empty()
        def product = Fixtures.product(id: 5L, services: [[id: 10L, name: 'gui']])
        def pipeline = Fixtures.pipeline(id: 20L, productId: 5L, serviceId: 10L)

        when:
        runner.run { ApplicationContext context ->
            context.getBean(MonitorPipelinesUseCase).status()
            context.getBean(MonitorPipelinesUseCase).overview()
            context.getBean(MonitorPipelinesUseCase).pipeline(20L, '30d')
            context.getBean(QueryEvidenceUseCase).product(5L)
        }

        then: 'only the short reads of the catalogue run in a transaction, the InfluxDB queries do not'
        transactions.log == ['begin read-only', 'begin read-only', 'commit', 'commit'] * 3
        calls == ['ping without transaction', 'load read-only', 'latest runs without transaction',
                  'load read-only', 'recent runs without transaction', 'DORA points without transaction',
                  'latest runs without transaction', 'load read-only', 'latest runs without transaction',
                  'evidence without transaction']
        1 * products.findAll() >> [product]
        1 * pipelines.findAll() >> [pipeline]
        2 * products.load(5L) >> Optional.of(product)
        1 * pipelines.load(20L) >> Optional.of(pipeline)
        1 * pipelines.findByProductId(5L) >> [pipeline]
        3 * pipelines.sharedMetricsTags() >> ([] as Set)
    }

    def "the catalog and pipeline queries run in read-only transactions, also when they read the settings"() {
        when:
        runner.run { ApplicationContext context ->
            context.getBean(ProductsUseCase).list(null)
            context.getBean(PipelinesUseCase).listForProduct(5L)
        }

        then:
        transactions.log == ['begin read-only', 'commit', 'begin read-only', 'begin read-only', 'commit', 'commit']
        1 * products.servicesPerProduct() >> [:]
        1 * pipelineCounts.pipelinesPerProduct() >> [:]
        1 * pipelineCounts.activePipelinesPerProduct() >> [:]
        1 * products.summaries() >> []
        1 * products.load(5L) >> Optional.of(Fixtures.product(id: 5L))
        1 * pipelines.findByProductId(5L) >> []
    }

    private static GlobalSettingsValues withoutLimit(Scanner scanner) {
        def bbh = bbhDefaults()
        copy(bbh, limits: bbh.limits().findAll { it.key != scanner })
    }

    static String transactionState() {
        if (!isActualTransactionActive()) {
            return 'without transaction'
        }
        isCurrentTransactionReadOnly() ? 'read-only' : 'read-write'
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        final List<String> log = []

        @Override
        protected Object doGetTransaction() {
            new Object()
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            log << (definition.readOnly ? 'begin read-only' : 'begin read-write')
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            log << 'commit'
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            log << 'rollback'
        }
    }
}
