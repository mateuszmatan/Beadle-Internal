package com.bbh.itss.dso.portal.config

import com.bbh.itss.dso.portal.application.UseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.in.PublishPipelineConfigsUseCase
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.ConfigSerializerPort
import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublishedConfigRepositoryPort
import com.bbh.itss.dso.portal.application.pipeline.port.in.QueryPipelinesUseCase
import com.bbh.itss.dso.portal.application.pipeline.port.out.PipelineRepositoryPort
import com.bbh.itss.dso.portal.application.settings.port.in.ManageGlobalSettingsUseCase
import com.bbh.itss.dso.portal.application.settings.port.in.UpdateGlobalSettingsCommand
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

class UseCaseConfigurationSpec extends Specification {

    def transactions = new RecordingTransactionManager()
    def repository = new InMemoryGlobalSettings()
    def publisher = new RecordingPublisher()
    def bbh = GlobalSettingsValues.bbhDefaults()
    ProductRepositoryPort products = Mock()
    PipelineCountsPort pipelineCounts = Mock()
    PipelineRepositoryPort pipelines = Mock()
    PublishedConfigRepositoryPort published = Mock()

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration, TransactionAutoConfiguration))
            .withUserConfiguration(UseCaseConfiguration)
            .withBean(PlatformTransactionManager, { transactions } as Supplier<PlatformTransactionManager>)
            .withBean(GlobalSettingsRepositoryPort, { repository } as Supplier<GlobalSettingsRepositoryPort>)
            .withBean(PublishPipelineConfigsUseCase, { publisher } as Supplier<PublishPipelineConfigsUseCase>,
                    { it.primary = true } as BeanDefinitionCustomizer)
            .withBean(PublishedConfigRepositoryPort, { published } as Supplier<PublishedConfigRepositoryPort>)
            .withBean(ConfigSerializerPort, { { Map config -> config.toString() } as ConfigSerializerPort }
                    as Supplier<ConfigSerializerPort>)
            .withBean(ProductRepositoryPort, { products } as Supplier<ProductRepositoryPort>)
            .withBean(PipelineCountsPort, { pipelineCounts } as Supplier<PipelineCountsPort>)
            .withBean(PipelineRepositoryPort, { pipelines } as Supplier<PipelineRepositoryPort>)
            .withBean(KeyGenerator, { { -> 'key' } as KeyGenerator } as Supplier<KeyGenerator>)
            .withBean(Clock, { Clock.systemUTC() } as Supplier<Clock>)

    def "every use case of the application layer is a bean behind a transactional proxy"() {
        expect:
        runner.run { ApplicationContext context ->
            def useCases = context.getBeansWithAnnotation(UseCase)
            assert useCases.keySet().containsAll(['globalSettingsService', 'productCatalogService', 'pipelineService',
                                                  'pipelineConfigService', 'pipelineConfigPublisher'])
            useCases.values().each { useCase ->
                assert AopUtils.isAopProxy(useCase)
                assert (useCase as Advised).advisors*.advice.any { it instanceof TransactionInterceptor }
            }
        }
    }

    def "a reading use case method runs in a read-only transaction"() {
        when:
        runner.run { ApplicationContext context -> context.getBean(ManageGlobalSettingsUseCase).current() }

        then:
        transactions.log == ['begin read-only', 'commit']
        repository.calls == ['load read-only']
    }

    def "a change is saved and published in one read-write transaction"() {
        when:
        runner.run { ApplicationContext context ->
            context.getBean(ManageGlobalSettingsUseCase).update(new UpdateGlobalSettingsCommand(1L,
                    bbh.withPlatform(bbh.platform().withJenkinsUrl('https://jenkins.bbh.com'))))
        }

        then:
        transactions.log == ['begin read-write', 'commit']
        repository.calls == ['load read-write', 'save read-write']
        publisher.calls == ['settingsChanged read-write']
        repository.stored.jenkinsUrl() == 'https://jenkins.bbh.com'
    }

    def "a domain exception rolls the transaction back: #reason"() {
        given:
        Throwable failure = null

        when:
        runner.run { ApplicationContext context ->
            try {
                context.getBean(ManageGlobalSettingsUseCase).update(command(bbh))
            } catch (RuntimeException e) {
                failure = e
            }
        }

        then:
        exception.isInstance(failure)
        transactions.log == ['begin read-write', 'rollback']
        publisher.calls == []

        where:
        reason                  | exception               | command
        'a concurrent change'   | ConflictException       | { GlobalSettingsValues v -> new UpdateGlobalSettingsCommand(0L, v) }
        'a broken business rule' | InvalidRequestException | { GlobalSettingsValues v ->
            new UpdateGlobalSettingsCommand(1L, new GlobalSettingsValues(v.platform(), v.deployment(),
                    v.limits().findAll { it.key != Scanner.SAST }, v.scans(), v.releaseGate(), v.serviceDefaults(),
                    v.goldenFix()))
        }
    }

    def "the catalog and pipeline queries run in read-only transactions, also when they read the settings"() {
        when:
        runner.run { ApplicationContext context ->
            context.getBean(QueryProductsUseCase).list(null)
            context.getBean(QueryPipelinesUseCase).listForProduct(5L)
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

    def "creating the settings at start-up runs in a read-write transaction"() {
        given:
        repository.stored = null

        when:
        runner.run { ApplicationContext context -> context.getBean(ManageGlobalSettingsUseCase).ensureExists() }

        then:
        transactions.log == ['begin read-write', 'commit']
        repository.calls == ['load read-write', 'save read-write']
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

    static class InMemoryGlobalSettings implements GlobalSettingsRepositoryPort {

        GlobalSettings stored = new GlobalSettings(GlobalSettingsValues.bbhDefaults(), 1,
                Instant.parse('2026-10-04T12:00:00Z'))
        final List<String> calls = []

        @Override
        Optional<GlobalSettings> load() {
            calls << 'load ' + transactionState()
            Optional.ofNullable(stored)
        }

        @Override
        GlobalSettings save(GlobalSettings settings) {
            calls << 'save ' + transactionState()
            stored = new GlobalSettings(settings.values(), settings.version() + 1, Instant.parse('2026-10-04T13:00:00Z'))
            stored
        }
    }

    static class RecordingPublisher implements PublishPipelineConfigsUseCase {

        final List<String> calls = []

        @Override
        void productChanged(long productId) {
            calls << 'productChanged ' + transactionState()
        }

        @Override
        void pipelineChanged(long pipelineId) {
            calls << 'pipelineChanged ' + transactionState()
        }

        @Override
        void settingsChanged() {
            calls << 'settingsChanged ' + transactionState()
        }

        @Override
        int publishAll() {
            calls << 'publishAll ' + transactionState()
            0
        }
    }
}
