package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import java.time.LocalDate

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

class JiraRegressionSpec extends BeadleInternalSpecification {

    def "the versions of a project come unreleased first, then the released ones newest first"() {
        when:
        def response = api.get('/api/internal/jira/projects/CERT/versions')

        then:
        response.status == 200
        def versions = response.json as List<Map>
        versions.size() >= 3
        versions.every { it.keySet() == ['name', 'released', 'releaseDate'] as Set && it.name.startsWith('CERT ') }
        def unreleased = versions.takeWhile { !it.released }
        def released = versions.drop(unreleased.size())
        !unreleased.empty && released.size() == 2 && released.every { it.released }
        released*.releaseDate == released*.releaseDate.sort(false).reverse()
        released.every { LocalDate.parse(it.releaseDate as String).isBefore(LocalDate.now(clock)) }
        api.get('/api/internal/jira/projects/CERT/versions').json == versions
    }

    def "the epics of a FixVersion and the stories of chosen epics are read for the change"() {
        given:
        def version = api.get('/api/internal/jira/projects/CERT/versions').json.find { it.releaseDate && !it.released }.name

        when:
        def epics = api.get("/api/internal/jira/projects/CERT/epics?fixVersion=${enc(version)}").json as List<Map>
        def chosen = epics*.key.take(2)
        def stories = api.get("/api/internal/jira/projects/CERT/stories?fixVersion=${enc(version)}&epicKeys=${chosen.join(',')}")
                .json as List<Map>

        then:
        !epics.empty
        epics.every { it.keySet() == ['key', 'summary', 'status', 'epicKey', 'updated'] as Set }
        epics.every { it.key ==~ /CERT-\d+/ && it.epicKey == null && it.summary && it.status }
        epics.every { LocalDate.parse(it.updated as String) }
        stories.every { it.epicKey in chosen && it.key ==~ /CERT-\d+/ }
        stories*.key.intersect(epics*.key).empty

        and: 'the calls are in the call log with the user and the correlation id of the request'
        def logged = callLog()
        logged*.operation == ['versions', 'epics', 'stories']
        logged.every { it.system == 'JIRA' && it.userName == USER && it.correlationId == correlationId && it.outcome == 'SUCCESS' }
        logged*.target == ['CERT', "CERT $version", "CERT $version ${chosen.join(',')}"]*.toString()
    }

    def "stories without epics are none, and a missing FixVersion is refused naming the field"() {
        when:
        def noEpics = api.get('/api/internal/jira/projects/CERT/stories?fixVersion=CERT%201.0')
        def noVersion = api.get('/api/internal/jira/projects/CERT/epics')

        then:
        noEpics.status == 200
        noEpics.json == []
        noVersion.status == 400
        noVersion.header('Content-Type').startsWith('application/problem+json')
        noVersion.json.problems == [[field: 'fixVersion', message: 'is required']]
        callLog() == []
    }

    private static String enc(String text) {
        encode(text, UTF_8).replace('+', '%20')
    }
}
