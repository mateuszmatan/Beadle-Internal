package com.bbh.itss.dso.portal.adapter.out.jira

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DemoJiraAdapterSpec extends Specification {

    static final LocalDate TODAY = LocalDate.parse('2026-10-07')

    def jira = new DemoJiraAdapter(Clock.fixed(Instant.parse('2026-10-07T10:00:00Z'), ZoneOffset.UTC))

    def "every project gets the same FixVersions, epics and stories on every call, newest first"() {
        when:
        def project = jira.project('CERT')
        def issues = project.issues()*.issue()
        def epics = issues.findAll { it.epicKey() == null }
        def stories = issues - epics

        then:
        !jira.connected()
        jira.project('CERT') == project
        jira.project('PAYHUB').issues()*.issue()*.summary() != issues*.summary()
        epics.size() == DemoJiraAdapter.EPICS_PER_PROJECT
        epics*.summary().unique().size() == epics.size()
        stories.every { story -> epics*.key().contains(story.epicKey()) }
        issues*.key().every { it ==~ /CERT-\d{3}/ }
        issues*.key().unique().size() == issues.size()
        issues*.updated() == issues*.updated().sort(false).reverse()
        issues.every { !it.updated().isAfter(TODAY) && it.updated().isAfter(TODAY.minusDays(130)) }
    }

    def "each project has two released FixVersions and one or two unreleased ones named after its key"() {
        expect:
        ['CERT', 'PAYHUB', 'DOCS', 'NAVC', 'SAFE', 'X'].every { key ->
            def versions = jira.versions(key)
            def released = versions.findAll { it.released() }
            def unreleased = versions - released
            assert versions*.name().every { it ==~ /$key \d\.\d/ }
            assert versions*.name().unique().size() == versions.size()
            assert released.size() == 2 && released.every { it.releaseDate().isBefore(TODAY) }
            assert unreleased.size() in [1, 2] && unreleased[0].releaseDate().isAfter(TODAY)
            assert unreleased.drop(1).every { it.releaseDate() == null }
            true
        }
    }

    def "the issues of a released FixVersion are done and every FixVersion has epics"() {
        given:
        def project = jira.project('CERT')
        def released = project.versions().findAll { it.released() }*.name()

        expect:
        project.issues().findAll { it.fixVersion() in released }.every { it.issue().status() == 'Done' }
        project.issues()*.issue()*.status().every { it in ['Done', 'In Review', 'In Progress', 'To Do'] }
        project.versions().every { version -> !jira.epics('CERT', version.name()).isEmpty() }
    }

    def "the epics of a FixVersion carry it or have a story that does, and their stories are those carrying it"() {
        given:
        def project = jira.project(key)
        def version = project.versions()[index].name()
        def carrying = project.issues().findAll { it.fixVersion() == version }*.issue()
        def expected = project.issues()*.issue().findAll { issue ->
            issue.epicKey() == null && carrying.any { it.key() == issue.key() || it.epicKey() == issue.key() }
        }

        when:
        def epics = jira.epics(key, " ${version.toLowerCase()} ")
        def stories = jira.stories(key, version, epics*.key())

        then:
        epics == expected
        stories == carrying.findAll { it.epicKey() != null }
        stories.every { it.epicKey() in epics*.key() }
        jira.stories(key, version, []) == []
        jira.epics(key, 'NOPE 9.9') == []

        where:
        key      | index
        'CERT'   | 0
        'CERT'   | 2
        'PAYHUB' | 1
        'NAVC'   | 2
    }

    def "some stories slip into the next FixVersion of their epic"() {
        expect:
        ['CERT', 'PAYHUB', 'DOCS', 'NAVC', 'SAFE', 'DEAL', 'CORP', 'ACCE'].any { key ->
            def issues = jira.project(key).issues()
            def epics = issues.findAll { it.issue().epicKey() == null }.collectEntries { [it.issue().key(), it] }
            issues.any { it.issue().epicKey() != null && it.fixVersion() != epics[it.issue().epicKey()].fixVersion() }
        }
    }

    def "issues are read by key"() {
        given:
        def epic = jira.project('CERT').issues()*.issue().find { it.epicKey() == null }

        expect:
        jira.issues('CERT', [epic.key(), 'CERT-1']) == [epic]
    }
}
