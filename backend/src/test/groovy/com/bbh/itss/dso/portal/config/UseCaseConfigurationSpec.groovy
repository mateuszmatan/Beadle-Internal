package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.UseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublicationLockPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublishedConfigRepositoryPort
import com.bbh.itss.dso.portal.application.evidence.port.in.QueryEvidenceUseCase
import com.bbh.itss.dso.portal.application.evidence.port.out.RunEvidencePort
import com.bbh.itss.dso.portal.application.monitoring.port.in.MonitorPipelinesUseCase
import com.bbh.itss.dso.portal.application.monitoring.port.out.DashboardLinksPort
import com.bbh.itss.dso.portal.application.monitoring.port.out.PipelineRunsPort
import com.bbh.itss.dso.portal.application.pipeline.port.in.PipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.application.settings.port.out.GlobalSettingsRepositoryPort
import com.bbh.itss.dso.portal.domain.pipeline.KeyGenerator
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues
import com.bbh.itss.dso.portal.domain.settings.Scanner
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.support.Fixtures
import org.springframework.aop.framework.Advised
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
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
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.dso.portal.support.Fixtures.copy

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()
    List<String> calls = []
    GlobalSettings stored = new GlobalSettings(GlobalSettingsValues.bbhDefaults(), 1, Instant.parse('2026-10-04T12:00:00Z'))
    def repository = [load: { -> calls << 'load ' + transactionState(); Optional.ofNullable(stored) },
                      save: { GlobalSettings settings ->
                          calls << 'save ' + transactionState()
                          stored = new GlobalSettings(settings.values(), settings.version() + 1, Instant.EPOCH)
                      }] as GlobalSettingsRepositoryPort
    def publisher = ['lockConfigurations', 'settingsChanged'].collectEntries { name ->
        [name, { -> calls << name + ' ' + transactionState() }]
    } as PublishPipelineConfigsUseCase
    def bbh = GlobalSettingsValues.bbhDefaults()
    ProductRepositoryPort products = Mock()
    PipelineCountsPort pipelineCounts = Mock()
    PipelineRepositoryPort pipelines = Mock()
    PublishedConfigRepositoryPort published = Mock()
    PublicationLockPort publicationLock = Mock()
    PipelineRunsPort runs = Mock()
    DashboardLinksPort dashboards = Mock()
    RunEvidencePort evidence = Mock()

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(GlobalSettingsRepositoryPort, { repository } as Supplier<GlobalSettingsRepositoryPort>)
            .withBean(PublishPipelineConfigsUseCase, { publisher } as Supplier<PublishPipelineConfigsUseCase>,
                    { it.primary = true } as BeanDefinitionCustomizer)
            .withBean(PublishedConfigRepositoryPort, { published } as Supplier<PublishedConfigRepositoryPort>)
            .withBean(PublicationLockPort, { publicationLock } as Supplier<PublicationLockPort>)
            .withBean(ConfigSerializerPort, { { Map config -> config.toString() } as ConfigSerializerPort }
                    as Supplier<ConfigSerializerPort>)
            .withBean(ProductRepositoryPort, { products } as Supplier<ProductRepositoryPort>)
            .withBean(PipelineCountsPort, { pipelineCounts } as Supplier<PipelineCountsPort>)
            .withBean(PipelineRepositoryPort, { pipelines } as Supplier<PipelineRepositoryPort>)
            .withBean(PipelineRunsPort, { runs } as Supplier<PipelineRunsPort>)
            .withBean(DashboardLinksPort, { dashboards } as Supplier<DashboardLinksPort>)
            .withBean(RunEvidencePort, { evidence } as Supplier<RunEvidencePort>)
            .withBean(KeyGenerator, { { -> 'key' } as KeyGenerator } as Supplier<KeyGenerator>)
            .withBean(Clock, { Clock.systemUTC() } as Supplier<Clock>)

    def "every use case of the application layer is a bean behind a transactional proxy"() {
        expect:
        runner.run { ApplicationContext context ->
            def useCases = context.getBeansWithAnnotation(UseCase)
            assert useCases.keySet().containsAll(['globalSettingsService', 'productCatalogService', 'pipelineService',
                                                  'pipelineConfigService', 'pipelineConfigPublisher',
                                                  'pipelineMonitoringService', 'changeEvidenceService',
                                                  'monitoringTargetsService'])
            useCases.values().each { useCase ->
                assert AopUtils.isAopProxy(useCase)
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

    def "a change is saved and published in one read-write transaction"() {
        when:
        runner.run { ApplicationContext context ->
            context.getBean(ManageGlobalSettingsUseCase).update(1L,
                    bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com')))
        }

        then:
        transactions.log == ['begin read-write', 'commit']
        calls == ['lockConfigurations read-write', 'load read-write', 'save read-write', 'settingsChanged read-write']
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
        calls == ['lockConfigurations read-write', 'load read-write']

        where:
        reason                   | version | values                             || exception
        'a concurrent change'    | 0L      | GlobalSettingsValues.bbhDefaults() || ConflictException
        'a broken business rule' | 1L      | withoutLimit(Scanner.SAST)         || InvalidRequestException
    }

    def "the monitoring and evidence pages query InfluxDB with no transaction and no database connection of their own"() {
        given:
        runs.configured() >> false
        dashboards.url() >> Optional.empty()

        when:
        runner.run { ApplicationContext context ->
            context.getBean(MonitorPipelinesUseCase).status()
            context.getBean(MonitorPipelinesUseCase).overview()
            context.getBean(QueryEvidenceUseCase).product(5L)
        }

        then: 'only the two short reads of the catalogue run in a transaction, the InfluxDB queries do not'
        transactions.log == ['begin read-only', 'begin read-only', 'commit', 'commit',
                             'begin read-only', 'begin read-only', 'commit', 'commit']
        1 * products.findAll() >> []
        1 * pipelines.findAll() >> []
        1 * products.load(5L) >> Optional.of(Fixtures.product(id: 5L))
        1 * pipelines.findByProductId(5L) >> []
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
        def bbh = GlobalSettingsValues.bbhDefaults()
        copy(bbh, limits: bbh.limits().findAll { it.key != scanner })
    }

    static String transactionState() {
        if (!TransactionSynchronizationManager.actualTransactionActive) {
            return 'without transaction'
        }
        TransactionSynchronizationManager.currentTransactionReadOnly ? 'read-only' : 'read-write'
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
