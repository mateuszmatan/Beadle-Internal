package com.bbh.itss.beadle.internal.support

import com.bbh.itss.beadle.internal.application.UseCase
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import jakarta.persistence.Converter
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Predicate

import static com.tngtech.archunit.base.DescribedPredicate.describe
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

abstract class ArchitectureSpecification extends Specification {

    static final String BASE = 'com.bbh.itss.beadle.internal'
    static final String DOMAIN = 'com.bbh.itss.beadle.internal.domain..'
    static final String APPLICATION = 'com.bbh.itss.beadle.internal.application..'
    static final String PORT_OUT = 'com.bbh.itss.beadle.internal.application..port.out..'
    static final String ADAPTER_IN = 'com.bbh.itss.beadle.internal.adapter.in..'
    static final String ADAPTER_OUT = 'com.bbh.itss.beadle.internal.adapter.out..'
    static final String WEB = 'com.bbh.itss.beadle.internal.adapter.in.web..'
    static final String PERSISTENCE = 'com.bbh.itss.beadle.internal.adapter.out.persistence..'
    static final String ADAPTER = 'com.bbh.itss.beadle.internal.adapter..'
    static final String CONFIG = 'com.bbh.itss.beadle.internal.config..'
    static final String[] FRAMEWORKS = ['jakarta..', 'org.springframework..', 'org.hibernate..', 'tools.jackson..',
                                        'com.fasterxml..', 'org.slf4j..']
    static final String[] HELPERS = ['org.apache.commons.lang3..', 'org.apache.commons.collections4..', 'lombok..']

    @Shared
    JavaClasses service = new ClassFileImporter()
            .importLocations([Location.of(application().protectionDomain.codeSource.location)])

    @Shared
    List<ArchRule> rules = sharedRules()

    abstract Class<?> application()

    abstract List<String> httpAdapters()

    def "the import covers the whole service"() {
        expect:
        service.contain(application())
        service.contain(UseCase)
        service.size() > 20
    }

    def "#rule.description"() {
        expect:
        holds rule

        where:
        rule << rules
    }

    private List<ArchRule> sharedRules() {
        [classes().that().doNotHaveFullyQualifiedName(application().name)
                 .should().resideInAnyPackage(DOMAIN, APPLICATION, ADAPTER, CONFIG),
         classes().that().resideInAPackage(BASE)
                 .should().haveFullyQualifiedName(application().name),
         classes().should().resideInAPackage('com.bbh.itss.beadle..'),
         classes().that().resideInAPackage(DOMAIN)
                 .should().onlyDependOnClassesThat().resideInAnyPackage('java..', DOMAIN, *HELPERS),
         classes().that().resideInAPackage(APPLICATION)
                 .should().onlyDependOnClassesThat().resideInAnyPackage('java..', DOMAIN, APPLICATION, *HELPERS),
         noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                 .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS),
         noClasses().that().resideInAPackage(ADAPTER_IN)
                 .should().dependOnClassesThat().resideInAPackage(ADAPTER_OUT),
         noClasses().that().resideInAPackage(ADAPTER_OUT)
                 .should().dependOnClassesThat().resideInAPackage(ADAPTER_IN),
         noClasses().that().resideInAPackage(ADAPTER_IN)
                 .should().dependOnClassesThat().resideInAPackage(PORT_OUT),
         classes().that(describedAs('are controllers or controller advice') { JavaClass type ->
             [RestController, RestControllerAdvice, Controller].any { type.isAnnotatedWith(it) }
         }).should().resideInAPackage(WEB),
         classes().that(describedAs('are persistence types') { JavaClass type ->
             [Entity, Embeddable, MappedSuperclass, Converter].any { type.isAnnotatedWith(it) } ||
                     type.isAssignableTo(Repository)
         }).should().resideInAPackage(PERSISTENCE).allowEmptyShould(true),
         noClasses().that().resideOutsideOfPackages(httpAdapters() as String[])
                 .should().dependOnClassesThat()
                 .resideInAnyPackage('org.springframework.web.client..', 'java.net.http..'),
         slices().matching('com.bbh.itss.beadle.internal.domain.(*)..').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.beadle.internal.application.(**)').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.beadle.internal.adapter.(**)').should().beFreeOfCycles(),
         classes().that().areAnnotatedWith(UseCase)
                 .should().resideInAPackage(APPLICATION).andShould(implementAnInPort()).allowEmptyShould(true),
         classes().should(notDependOnOtherUseCases())]
    }

    protected boolean holds(ArchRule rule) {
        rule.check(service)
        true
    }

    private static DescribedPredicate<JavaClass> describedAs(String description, Closure<Boolean> test) {
        describe(description, { JavaClass type -> test(type) } as Predicate<JavaClass>)
    }

    private static ArchCondition<JavaClass> implementAnInPort() {
        new ArchCondition<JavaClass>('implement an interface of a port.in package') {
            @Override
            void check(JavaClass useCase, ConditionEvents events) {
                boolean implementsPort = useCase.allRawInterfaces.any { it.packageName.contains('.port.in') }
                events.add(new SimpleConditionEvent(useCase, implementsPort,
                        "${useCase.name} ${implementsPort ? 'implements' : 'does not implement'} an in port"))
            }
        }
    }

    private static ArchCondition<JavaClass> notDependOnOtherUseCases() {
        new ArchCondition<JavaClass>('not depend on use case classes other than themselves') {
            @Override
            void check(JavaClass type, ConditionEvents events) {
                type.directDependenciesFromSelf
                        .findAll { it.targetClass != type && it.targetClass.isAnnotatedWith(UseCase) }
                        .each { events.add(violated(it, it.description)) }
            }
        }
    }
}
