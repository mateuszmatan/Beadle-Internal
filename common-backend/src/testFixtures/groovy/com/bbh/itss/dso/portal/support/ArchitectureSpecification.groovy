package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.application.UseCase
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

    static final String DOMAIN = 'com.bbh.itss.dso.portal.domain..'
    static final String APPLICATION = 'com.bbh.itss.dso.portal.application..'
    static final String ADAPTER_IN = 'com.bbh.itss.dso.portal.adapter.in..'
    static final String ADAPTER_OUT = 'com.bbh.itss.dso.portal.adapter.out..'
    static final String WEB = 'com.bbh.itss.dso.portal.adapter.in.web..'
    static final String PERSISTENCE = 'com.bbh.itss.dso.portal.adapter.out.persistence..'
    static final String ADAPTER = 'com.bbh.itss.dso.portal.adapter..'
    static final String CONFIG = 'com.bbh.itss.dso.portal.config..'
    static final String[] FRAMEWORKS = ['jakarta..', 'org.springframework..', 'org.hibernate..', 'tools.jackson..',
                                        'com.fasterxml..', 'org.slf4j..']
    static final String[] HELPERS = ['org.apache.commons.lang3..', 'org.apache.commons.collections4..', 'lombok..']

    @Shared
    JavaClasses portal = new ClassFileImporter().importLocations([application(), UseCase]
            .collect { Location.of(it.protectionDomain.codeSource.location) })

    @Shared
    List<ArchRule> rules = sharedRules() + applicationRules()

    abstract Class<?> application()

    List<ArchRule> applicationRules() {
        []
    }

    def "the import covers the application and the code it shares"() {
        expect:
        portal.contain(application())
        portal.contain(UseCase)
        portal.size() > 50
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
         classes().that().resideInAPackage('com.bbh.itss.dso.portal')
                 .should().haveFullyQualifiedName(application().name),
         classes().should().resideInAPackage('com.bbh.itss.dso..'),
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
                 .should().dependOnClassesThat()
                 .resideInAPackage('com.bbh.itss.dso.portal.application..port.out..'),
         classes().that(describedAs('are controllers or controller advice') { JavaClass type ->
             [RestController, RestControllerAdvice, Controller].any { type.isAnnotatedWith(it) }
         }).should().resideInAPackage(WEB),
         classes().that(describedAs('are persistence types') { JavaClass type ->
             [Entity, Embeddable, MappedSuperclass, Converter].any { type.isAnnotatedWith(it) } ||
                     type.isAssignableTo(Repository)
         }).should().resideInAPackage(PERSISTENCE),
         slices().matching('com.bbh.itss.dso.portal.domain.(*)..').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.dso.portal.application.(**)').should().beFreeOfCycles(),
         slices().matching('com.bbh.itss.dso.portal.adapter.(**)').should().beFreeOfCycles(),
         classes().that().areAnnotatedWith(UseCase)
                 .should().resideInAPackage(APPLICATION).andShould(implementAnInPort()),
         classes().should(notDependOnOtherUseCases())]
    }

    protected boolean holds(ArchRule rule) {
        rule.check(portal)
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
