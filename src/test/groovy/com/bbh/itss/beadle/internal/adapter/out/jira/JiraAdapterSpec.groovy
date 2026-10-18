package com.bbh.itss.beadle.internal.adapter.out.jira

import com.bbh.itss.beadle.internal.domain.shared.RefusedException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.LocalDate

import static com.bbh.itss.beadle.internal.adapter.out.jira.JiraAdapter.ORDER
import static com.bbh.itss.beadle.internal.support.Fixtures.issue
import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class JiraAdapterSpec extends Specification {

    static final String URL = 'https://jira.bbh.com'
    static final String SEARCH = URL + '/rest/api/2/search'
    static final List<String> FIELDS = ['summary', 'status', 'updated', 'parent', 'customfield_10014']

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def jira = new JiraAdapter(new JiraProperties(URL + '/', 'secret-token', 'customfield_10014'), builder)

    def "the versions of a project are read with their release dates, if any"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/project/CERT/versions')).andExpect(method(GET))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andRespond(withSuccess('''[
                    {"id":"1","name":"CERT 1.3","released":true,"releaseDate":"2026-09-01","archived":false},
                    {"id":"2","name":"CERT 1.4","released":false,"releaseDate":"2026-11-01"},
                    {"id":"3","name":"CERT 1.5","released":false}]''', APPLICATION_JSON))

        when:
        def versions = jira.versions('CERT')

        then:
        jira.connected()
        versions == [version(name: 'CERT 1.3', released: true, releaseDate: LocalDate.parse('2026-09-01')),
                     version(name: 'CERT 1.4'), version(name: 'CERT 1.5', releaseDate: null)]
        server.verify()
    }

    def "the epics of a FixVersion are searched by JQL, page by page, newest change first"() {
        given: 'no issue of the FixVersion belongs to an epic'
        server.expect(requestTo(SEARCH)).andExpect(method(POST))
                .andExpect(body([jql   : 'project = "CERT" AND issuetype != Epic AND fixVersion = "CERT 1.4"' + ORDER,
                                 startAt: 0, maxResults: 100, fields: FIELDS]))
                .andRespond(withSuccess('{"startAt":0,"maxResults":100,"total":1,"issues":[' +
                        '{"key":"CERT-30","fields":{"summary":"A bug on its own","customfield_10014":null,"parent":null}}]}',
                        APPLICATION_JSON))
        def jql = 'project = "CERT" AND issuetype = Epic AND fixVersion = "CERT 1.4"' + ORDER
        server.expect(requestTo(SEARCH)).andExpect(method(POST))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andExpect(body([jql: jql, startAt: 0, maxResults: 100,
                                 fields: ['summary', 'status', 'updated', 'parent', 'customfield_10014']]))
                .andRespond(withSuccess('''{"startAt":0,"maxResults":1,"total":2,"issues":[
                    {"key":"CERT-12","fields":{"summary":"Single sign-on with BBH SSO","status":{"name":"In Progress"},
                     "updated":"2026-10-01T23:30:00.000+0200","customfield_10014":null,"parent":null}}]}''', APPLICATION_JSON))
        server.expect(requestTo(SEARCH)).andExpect(body([jql: jql, startAt: 1, maxResults: 100,
                                                         fields: ['summary', 'status', 'updated', 'parent', 'customfield_10014']]))
                .andRespond(withSuccess('''{"startAt":1,"maxResults":1,"total":2,"issues":[
                    {"key":"CERT-13","fields":{"summary":"Audit trail","status":{"name":"To Do"},"updated":"2026-09-20T08:00:00.000+0000"}}]}''',
                        APPLICATION_JSON))

        when:
        def epics = jira.epics('CERT', 'CERT 1.4')

        then:
        epics == [issue(), issue(key: 'CERT-13', summary: 'Audit trail', status: 'To Do', updated: LocalDate.parse('2026-09-20'))]
        server.verify()
    }

    def "an epic without the FixVersion comes with the epics that carry it when one of its issues is in the FixVersion"() {
        given: 'the issues of the FixVersion name their epics by epic link or parent'
        server.expect(requestTo(SEARCH))
                .andExpect(body([jql   : 'project = "CERT" AND issuetype != Epic AND fixVersion = "CERT 1.4"' + ORDER,
                                 startAt: 0, maxResults: 100, fields: FIELDS]))
                .andRespond(withSuccess('''{"startAt":0,"maxResults":100,"total":4,"issues":[
                    {"key":"CERT-20","fields":{"summary":"Map SSO groups","customfield_10014":"CERT-14"}},
                    {"key":"CERT-21","fields":{"summary":"Export the audit trail","parent":{"key":"CERT-12"}}},
                    {"key":"CERT-22","fields":{"summary":"Nothing linked"}},
                    {"key":"CERT-23","fields":{"summary":"Sign out everywhere","customfield_10014":"CERT-14"}}]}''', APPLICATION_JSON))

        and: 'those epics are searched with the ones that carry the FixVersion, without validating their keys'
        server.expect(requestTo(SEARCH))
                .andExpect(body([jql          : 'project = "CERT" AND issuetype = Epic AND ' +
                        '(fixVersion = "CERT 1.4" OR key in ("CERT-14", "CERT-12"))' + ORDER,
                                 startAt      : 0, maxResults: 100, fields: FIELDS, validateQuery: false]))
                .andRespond(withSuccess('''{"startAt":0,"maxResults":100,"total":3,"issues":[
                    {"key":"CERT-14","fields":{"summary":"Single sign-on","status":{"name":"In Progress"},
                     "updated":"2026-10-03T08:00:00.000+0000"}},
                    {"key":"CERT-12","fields":{"summary":"Single sign-on with BBH SSO","status":{"name":"In Progress"},
                     "updated":"2026-10-01T23:30:00.000+0200"}},
                    {"key":"CERT-11","fields":{"summary":"Carries the FixVersion","status":{"name":"To Do"},
                     "updated":"2026-09-01T08:00:00.000+0000"}}]}''', APPLICATION_JSON))

        expect:
        jira.epics('CERT', 'CERT 1.4') == [
                issue(key: 'CERT-14', summary: 'Single sign-on', updated: LocalDate.parse('2026-10-03')),
                issue(),
                issue(key: 'CERT-11', summary: 'Carries the FixVersion', status: 'To Do', updated: LocalDate.parse('2026-09-01'))]
        server.verify()
    }

    def "the epics of more than #batch issues of the FixVersion are searched #batch keys at a time, newest change first"() {
        given:
        def stories = (1..101).collect { [key: "CERT-${2000 + it}".toString(), fields: [customfield_10014: "CERT-${1000 + it}".toString()]] }
        [stories.take(100), stories.drop(100)].eachWithIndex { List page, int index ->
            server.expect(requestTo(SEARCH))
                    .andExpect(body([jql   : 'project = "CERT" AND issuetype != Epic AND fixVersion = "CERT 1.4"' + ORDER,
                                     startAt: index * 100, maxResults: 100, fields: FIELDS]))
                    .andRespond(withSuccess(JsonOutput.toJson([startAt: index * 100, maxResults: 100, total: 101, issues: page]), APPLICATION_JSON))
        }
        def keys = (1001..1101).collect { '"CERT-' + it + '"' }
        server.expect(requestTo(SEARCH))
                .andExpect(body([jql          : 'project = "CERT" AND issuetype = Epic AND (fixVersion = "CERT 1.4" OR key in (' +
                        keys.take(100).join(', ') + '))' + ORDER, startAt: 0, maxResults: 100, fields: FIELDS, validateQuery: false]))
                .andRespond(withSuccess('''{"startAt":0,"maxResults":100,"total":2,"issues":[
                    {"key":"CERT-1001","fields":{"summary":"Older","updated":"2026-10-01T08:00:00.000+0000"}},
                    {"key":"CERT-9","fields":{"summary":"Same time, lower number","updated":"2026-10-01T08:00:00.000+0000"}}]}''',
                        APPLICATION_JSON))
        server.expect(requestTo(SEARCH))
                .andExpect(body([jql          : 'project = "CERT" AND issuetype = Epic AND key in (' + keys.drop(100).join(', ') + ')' + ORDER,
                                 startAt      : 0, maxResults: 100, fields: FIELDS, validateQuery: false]))
                .andRespond(withSuccess('''{"startAt":0,"maxResults":100,"total":2,"issues":[
                    {"key":"CERT-1101","fields":{"summary":"Newest","updated":"2026-10-05T08:00:00.000+0000"}},
                    {"key":"CERT-1050","fields":{"summary":"Never updated"}}]}''', APPLICATION_JSON))

        expect:
        jira.epics('CERT', 'CERT 1.4')*.key() == ['CERT-1101', 'CERT-9', 'CERT-1001', 'CERT-1050']
        server.verify()

        where:
        batch = JiraAdapter.EPIC_BATCH
    }

    def "the stories of chosen epics are searched by their epic link or parent, which becomes their epic key"() {
        given:
        def jql = 'project = "CERT" AND issuetype != Epic AND fixVersion = "CERT 1.4" AND ' +
                '(parent in ("CERT-12", "CERT-13") OR cf[10014] in ("CERT-12", "CERT-13"))' + ORDER
        server.expect(requestTo(SEARCH)).andExpect(method(POST))
                .andExpect(body([jql: jql, startAt: 0, maxResults: 100,
                                 fields: ['summary', 'status', 'updated', 'parent', 'customfield_10014']]))
                .andRespond(withSuccess('''{"startAt":0,"maxResults":100,"total":3,"issues":[
                    {"key":"CERT-20","fields":{"summary":"Map SSO groups","status":{"name":"Done"},
                     "updated":"2026-10-02T10:00:00.000+0000","customfield_10014":"CERT-12"}},
                    {"key":"CERT-21","fields":{"summary":"Export the audit trail","status":{"name":"In Review"},
                     "updated":"2026-10-01T10:00:00.000+0000","parent":{"key":"CERT-13"}}},
                    {"key":"CERT-22","fields":{"summary":"Nothing linked","status":null,"updated":null}}]}''', APPLICATION_JSON))

        when:
        def stories = jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13'])

        then:
        stories == [issue(key: 'CERT-20', summary: 'Map SSO groups', status: 'Done', epicKey: 'CERT-12', updated: LocalDate.parse('2026-10-02')),
                    issue(key: 'CERT-21', summary: 'Export the audit trail', status: 'In Review', epicKey: 'CERT-13'),
                    issue(key: 'CERT-22', summary: 'Nothing linked', status: null, epicKey: null, updated: null)]
        server.verify()
    }

    def "an epic link field given by name is quoted in the JQL and quotes in values are escaped"() {
        given:
        def named = new JiraAdapter(new JiraProperties(URL, 'secret-token', 'Epic Link'), builder)
        server.expect(requestTo(SEARCH)).andExpect { ClientHttpRequest request ->
            assert body(request).jql == 'project = "CE\\"RT" AND issuetype != Epic AND fixVersion = "1 \\"beta\\"" AND ' +
                    '(parent in ("CERT-1") OR "Epic Link" in ("CERT-1"))' + ORDER
            assert body(request).fields == ['summary', 'status', 'updated', 'parent', 'Epic Link']
        }.andRespond(withSuccess('{"startAt":0,"maxResults":100,"total":0,"issues":[]}', APPLICATION_JSON))

        expect:
        named.stories('CE"RT', '1 "beta"', ['CERT-1']) == []
    }

    def "an unknown project has no versions"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/project/NOPE/versions')).andRespond(withResourceNotFound())

        when:
        jira.versions('NOPE')

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == 'Jira project NOPE does not exist'
    }

    def "a Jira refusal of #operation is reported with its reason"() {
        given:
        server.expect(requestTo(url)).andRespond(response)

        when:
        call(jira)

        then:
        def refused = thrown(type)
        refused.message.startsWith("Jira $failed $operation: $reason")

        where:
        operation                       | url                                        | response                                      | call                                                                || failed       | reason
        'read the versions of CERT'     | URL + '/rest/api/2/project/CERT/versions'  | withServerError()                             | ({ JiraAdapter it -> it.versions('CERT') })                          || 'could not'  | '500 Internal Server Error'
        'read the epics of CERT 1.4'    | SEARCH                                     | withBadRequest().body('{"errorMessages":["Field cf[10014] does not exist"]}') | ({ JiraAdapter it -> it.epics('CERT', 'CERT 1.4') }) || 'refused to' | '400 Bad Request'
        'read the stories of CERT 1.4'  | SEARCH                                     | withSuccess('{"startAt":0,"total":1,"issues":"oops"}', APPLICATION_JSON) | ({ JiraAdapter it -> it.stories('CERT', 'CERT 1.4', ['CERT-12']) }) || 'could not'  | 'Error while extracting response'
        type = failed == 'refused to' ? RefusedException : UncheckedIOException
    }

    private static RequestMatcher body(Map expected) {
        { ClientHttpRequest request -> assert body(request) == expected } as RequestMatcher
    }

    private static Map body(ClientHttpRequest request) {
        new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) as Map
    }
}
