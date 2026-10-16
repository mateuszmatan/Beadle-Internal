package com.bbh.itss.beadle.internal.application.jira

import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.LocalDate

import static com.bbh.itss.beadle.internal.application.jira.JiraService.REQUIRED
import static com.bbh.itss.beadle.internal.support.Fixtures.issue
import static com.bbh.itss.beadle.internal.support.Fixtures.version

class JiraServiceSpec extends Specification {

    JiraPort jira = Mock()
    def service = new JiraService(jira)

    def "the versions of a project come unreleased first, newest first"() {
        given:
        def released = version(name: 'CERT 1.2', released: true, releaseDate: LocalDate.parse('2026-08-01'))
        def olderReleased = version(name: 'CERT 1.1', released: true, releaseDate: LocalDate.parse('2026-06-01'))
        def planned = version(name: 'CERT 1.4', releaseDate: LocalDate.parse('2026-11-01'))
        def unplanned = version(name: 'CERT 1.5', releaseDate: null)

        when:
        def versions = service.versions(' CERT ')

        then:
        1 * jira.versions('CERT') >> [olderReleased, planned, released, unplanned]
        versions == [unplanned, planned, released, olderReleased]
    }

    def "the epics of a FixVersion are asked with the trimmed project and version"() {
        given:
        def epics = [issue(), issue(key: 'CERT-13')]

        when:
        def found = service.epics('CERT', ' CERT 1.4 ')

        then:
        1 * jira.epics('CERT', 'CERT 1.4') >> epics
        found == epics
    }

    def "the stories of chosen epics are asked with the cleaned keys, and none without a key"() {
        given:
        def stories = [issue(key: 'CERT-20', epicKey: 'CERT-12')]

        when:
        def found = service.stories('CERT', 'CERT 1.4', [' CERT-12', 'CERT-13', '', null, 'CERT-12'])
        def none = service.stories('CERT', 'CERT 1.4', [' ', null])

        then:
        1 * jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13']) >> stories
        0 * jira._
        found == stories
        none == []
    }

    def "#call without #field is refused before Jira is asked"() {
        when:
        call(service)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == [field]
        refused.problems*.message == [REQUIRED]
        0 * jira._

        where:
        call                                                        | field
        ({ JiraService it -> it.versions(' ') })                    | 'key'
        ({ JiraService it -> it.epics('CERT', null) })              | 'fixVersion'
        ({ JiraService it -> it.epics('', 'CERT 1.4') })            | 'key'
        ({ JiraService it -> it.stories('CERT', ' ', ['CERT-12']) }) | 'fixVersion'
    }
}
