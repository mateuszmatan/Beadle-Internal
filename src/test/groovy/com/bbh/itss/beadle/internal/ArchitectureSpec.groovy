package com.bbh.itss.beadle.internal

import com.bbh.itss.beadle.internal.application.UseCase
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import jakarta.persistence.Converter
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.Repository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Predicate

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * The hexagon of Beadle-Internal: a domain on plain Java, use cases on the domain and their ports, adapters around them
 * that do not know each other, and the configuration that wires them.
 */
class ArchitectureSpec extends Specification {

    static final String BASE = 'com.bbh.itss.beadle.internal'
    static final String DOMAIN = BASE + '.domain..'
    static final String APPLICATION = BASE + '.application..'
    static final String ADAPTER = BASE + '.adapter..'
    static final String ADAPTER_IN = BASE + '.adapter.in..'
    static final String ADAPTER_OUT = BASE + '.adapter.out..'
    static final String CONFIG = BASE + '.config..'
    static final String[] HELPERS = ['java..', 'org.apache.commons.lang3..', 'org.apache.commons.collections4..', 'lombok..']
    static final String[] HTTP_ADAPTERS = ['http', 'jira', 'cybertrack', 'confluence', 'qtest', 'gui']
            .collect { "${BASE}.adapter.out.${it}..".toString() }

    @Shared
    JavaClasses service = new ClassFileImporter()
            .importLocations([Location.of(BeadleInternalApplication.protectionDomain.codeSource.location)])

    def "the import covers the whole service"() {
        expect:
        service.contain(BeadleInternalApplication)
        service.size() > 20
    }

    def "#rule.description"() {
        expect:
        rule.check(service)

        where:
        rule << rules()
    }

    private static List<ArchRule> rules() {
        [classes().that().doNotHaveFullyQualifiedName(BeadleInternalApplication.name)
                 .should().resideInAnyPackage(DOMAIN, APPLICATION, ADAPTER, CONFIG),
         classes().that().resideInAPackage(DOMAIN).should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, *HELPERS),
         classes().that().resideInAPackage(APPLICATION)
                 .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, APPLICATION, 'org.slf4j..', *HELPERS),
         noClasses().that().resideInAPackage(ADAPTER_IN).should().dependOnClassesThat().resideInAPackage(ADAPTER_OUT),
         noClasses().that().resideInAPackage(ADAPTER_OUT).should().dependOnClassesThat().resideInAPackage(ADAPTER_IN),
         noClasses().that().resideInAPackage(ADAPTER_IN).should().dependOnClassesThat().resideInAPackage(BASE + '.application..port.out..'),
         noClasses().that().resideInAPackage(ADAPTER).should().dependOnClassesThat().resideInAPackage(CONFIG),
         classes().that(are('controllers or controller advice') { [RestController, RestControllerAdvice].any(it.&isAnnotatedWith) })
                 .should().resideInAPackage(BASE + '.adapter.in.web..'),
         classes().that(are('persistence types') { [Entity, Embeddable, Converter].any(it.&isAnnotatedWith) || it.isAssignableTo(Repository) })
                 .should().resideInAPackage(BASE + '.adapter.out.persistence..'),
         noClasses().that().resideOutsideOfPackages(HTTP_ADAPTERS)
                 .should().dependOnClassesThat().resideInAnyPackage('org.springframework.web.client..', 'java.net.http..'),
         slices().matching(BASE + '.domain.(*)..').should().beFreeOfCycles(),
         slices().matching(BASE + '.application.(**)').should().beFreeOfCycles(),
         slices().matching(BASE + '.adapter.(**)').should().beFreeOfCycles(),
         classes().that().areAnnotatedWith(UseCase).should().resideInAPackage(APPLICATION)
                 .andShould().implement(are('a port.in interface') { it.packageName.contains('.port.in') }),
         classes().that(are('implementations of an out port') { type ->
             !type.isInterface() && type.allRawInterfaces.any { it.packageName.contains('.port.out') }
         }).should().resideInAPackage(ADAPTER_OUT),
         methods().that().areAnnotatedWith(Scheduled).should().beDeclaredInClassesThat().resideInAPackage(ADAPTER_IN),
         classes().that().haveSimpleNameStartingWith('Demo').and().areAnnotatedWith(Component)
                 .should().beAnnotatedWith(DescribedPredicate.describe('@Profile("local")', { JavaAnnotation<?> annotation ->
                     annotation.rawType.isEquivalentTo(Profile) && annotation.get('value').map { it as List }.orElse([]) == ['local']
                 } as Predicate<JavaAnnotation<?>>)),
         classes().should(new ArchCondition<JavaClass>('not depend on use case classes other than themselves') {
             @Override
             void check(JavaClass type, ConditionEvents events) {
                 type.directDependenciesFromSelf
                         .findAll { it.targetClass != type && it.targetClass.isAnnotatedWith(UseCase) }
                         .each { events.add(violated(it, it.description)) }
             }
         })]
    }

    private static DescribedPredicate<JavaClass> are(String description, Closure<Boolean> test) {
        DescribedPredicate.describe(description, { JavaClass type -> test(type) } as Predicate<JavaClass>)
    }
}
