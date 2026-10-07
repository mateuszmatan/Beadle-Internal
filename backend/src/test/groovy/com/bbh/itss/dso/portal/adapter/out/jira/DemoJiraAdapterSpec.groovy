package com.bbh.itss.dso.portal.adapter.out.jira

import com.bbh.itss.dso.portal.domain.change.DateRange
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.adapter.out.jira.DemoJiraAdapter.EPICS_PER_PROJECT
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class DemoJiraAdapterSpec extends Specification {

    static final LocalDate TODAY = LocalDate.parse('2026-10-07')
    static final DateRange HALF_YEAR = new DateRange(TODAY.minusDays(240), TODAY)

    def jira = new DemoJiraAdapter(fixed(Instant.parse('2026-10-07T10:00:00Z'), UTC))

    def "every project gets the same epics and stories on every call, newest first"() {
        when:
        def issues = jira.project('CERT')
        def epics = issues.findAll { it.epicKey() == null }
        def stories = issues - epics

        then:
        !jira.connected()
        jira.project('CERT') == issues
        jira.project('PAYHUB')*.summary() != issues*.summary()
        epics.size() == EPICS_PER_PROJECT
        epics*.summary().unique().size() == epics.size()
        stories.every { story -> epics*.key().contains(story.epicKey()) }
        issues*.key().every { it ==~ /CERT-\d{3}/ }
        issues*.key().unique().size() == issues.size()
        issues*.updated() == issues*.updated().sort(false).reverse()
        issues.every { !it.updated().isAfter(TODAY) && it.updated().isAfter(TODAY.minusDays(240)) }
        issues.every { it.status() in ['Done', 'In Progress', 'In Review'] }
    }

    def "epics, their stories and issues by key are filtered by the update dates"() {
        given:
        def all = jira.project('CERT')
        def epic = all.find { it.epicKey() == null && all.any { story -> story.epicKey() == it.key() } }
        def recent = new DateRange(TODAY.minusDays(30), TODAY)

        expect:
        jira.epics('CERT', HALF_YEAR) == all.findAll { it.epicKey() == null }
        jira.epics('CERT', recent) == all.findAll { it.epicKey() == null && recent.contains(it.updated()) }
        jira.stories('CERT', [epic.key()], HALF_YEAR) == all.findAll { it.epicKey() == epic.key() }
        jira.stories('CERT', [], HALF_YEAR) == []
        jira.issues('CERT', [epic.key(), 'CERT-1']) == [epic]
    }
}
