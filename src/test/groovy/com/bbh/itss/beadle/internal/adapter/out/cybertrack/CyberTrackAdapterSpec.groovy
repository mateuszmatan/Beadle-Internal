package com.bbh.itss.beadle.internal.adapter.out.cybertrack

import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket
import groovy.json.JsonSlurper
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.CREATE
import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.NO_FIELDS
import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.NO_KEY
import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.READ
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class CyberTrackAdapterSpec extends Specification {

    static final String URL = 'https://cybertrack.bbh.com'
    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def cyberTrack = adapter('SCP', 'Security Task')

    def "the secure coding ticket is created in the configured CyberTrack project with its summary and description"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/issue')).andExpect(method(POST))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect { ClientHttpRequest request ->
                    assert new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) ==
                            [fields: [project    : [key: 'SCP'], issuetype: [name: 'Security Task'],
                                      summary    : 'APO-12345_CertScanner-10152026',
                                      description: '''\
                                          APO number: APO-12345
                                          Application: CertScanner
                                          Implementation date: 10152026
                                          Bitbucket URL (SAST scan): https://bitbucket.bbh.com/projects/CERT/repos/cert
                                          Artifact link (OSA - Nexus IQ scan): https://jenkins.bbh.com/job/CERT/job/cert-release/
                                          QC application link (DAST scan): https://cert.qc.bbh.com
                                          ProTech change: CHG0031001'''.stripIndent()]]
                }
                .andRespond(withStatus(CREATED).contentType(APPLICATION_JSON)
                        .body('{"id":"10001","key":"SCP-1234","self":"https://cybertrack.bbh.com/rest/api/2/issue/10001"}'))

        when:
        def created = cyberTrack.create(ticket())

        then:
        cyberTrack.connected()
        created == new CreatedTicket('SCP-1234', URL + '/browse/SCP-1234')
        server.verify()
    }

    def "a CyberTrack answer of #problem is refused with the reason"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/issue')).andRespond(response)

        when:
        cyberTrack.create(ticket())

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('CyberTrack could not ' + CREATE + ': ' + reason)
        refused.message.length() <= ('CyberTrack could not ' + CREATE + ': ').length() + 300

        where:
        problem               | response                                                                        || reason
        'a refusal'           | withBadRequest().body('{"errors":{"summary":"Summary is required."}}')          || '400 Bad Request'
        'a missing key'       | withSuccess('{"id":"10001"}', APPLICATION_JSON)                                 || NO_KEY
        'an empty key'        | withSuccess('{"key":" "}', APPLICATION_JSON)                                    || NO_KEY
        'a long server error' | withServerError().body('{"errorMessages":["' + 'x' * 600 + '"]}')               || '500 Internal Server Error'
    }

    def "an unreachable CyberTrack is refused with the reason"() {
        given:
        def unreachable = new CyberTrackAdapter(new CyberTrackProperties('http://127.0.0.1:1', 'token', 'SCP', 'Task'),
                RestClient.builder())

        when:
        unreachable.create(ticket())

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('CyberTrack could not ' + CREATE + ': I/O error')
    }

    def "a ticket is read with its status and the time of its last change in #notation"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/issue/SCP-1234?fields=status,updated')).andExpect(method(GET))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andRespond(withSuccess('{"key":"SCP-1234","fields":{"status":{"name":"In Progress"},"updated":"' +
                        updated + '"}}', APPLICATION_JSON))

        expect:
        cyberTrack.read('SCP-1234') == new CyberTrackTicket('SCP-1234', 'In Progress', URL + '/browse/SCP-1234', NOW)

        where:
        notation            | updated
        'Atlassian notation' | '2026-10-15T11:00:00.000+0200'
        'ISO notation'      | '2026-10-15T09:00:00Z'
    }

    def "an unknown ticket is not found and a CyberTrack answer of #problem is refused when reading"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/issue/SCP-9?fields=status,updated')).andRespond(response)

        when:
        cyberTrack.read('SCP-9')

        then:
        def refused = thrown(exception)
        refused.message.contains(reason)

        where:
        problem          | response                                                     || exception              | reason
        'not found'      | withResourceNotFound().body('{"errorMessages":["Issue Does Not Exist"]}') || NoSuchElementException | 'CyberTrack ticket SCP-9 does not exist'
        'a server error' | withServerError().body('{"errorMessages":["boom"]}')         || UncheckedIOException   | 'CyberTrack could not ' + READ + ' SCP-9: 500'
        'no fields'      | withSuccess('{"key":"SCP-9"}', APPLICATION_JSON)             || UncheckedIOException   | NO_FIELDS + 'SCP-9'
    }

    def "#count tickets are searched by key without validating the query, #batch keys at a time"() {
        given:
        def keys = (1..count).collect { "SCP-$it" as String }
        def batches = keys.collate(CyberTrackAdapter.BATCH)
        batches.each { List<String> batchKeys ->
            server.expect(requestTo(URL + '/rest/api/2/search')).andExpect(method(POST))
                    .andExpect(header('Authorization', 'Bearer secret-token'))
                    .andExpect { ClientHttpRequest request ->
                        assert new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) ==
                                [jql          : 'key in (' + batchKeys.collect { '"' + it + '"' }.join(', ') + ')',
                                 startAt      : 0, maxResults: batchKeys.size(), validateQuery: false,
                                 fields       : ['status', 'updated']]
                    }
                    .andRespond(withSuccess('{"total":' + batchKeys.size() + ',"issues":[' + batchKeys.collect {
                        '{"key":"' + it + '","fields":{"status":{"name":"Done"},"updated":"2026-10-15T11:00:00.000+0200"}}'
                    }.join(',') + ']}', APPLICATION_JSON))
        }

        expect:
        cyberTrack.readAll(keys + keys.take(1)) == keys.collect { new CyberTrackTicket(it, 'Done', URL + '/browse/' + it, NOW) }
        server.verify()

        where:
        count << [1, 50, 51]
        batch = CyberTrackAdapter.BATCH
    }

    def "a ticket CyberTrack does not answer, or answers without fields, is left out of a search"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/search'))
                .andRespond(withSuccess('{"total":2,"issues":[{"key":"SCP-1","fields":{"status":{"name":"To Do"}}},' +
                        '{"key":"SCP-2"},{"fields":{"status":{"name":"Done"}}}]}', APPLICATION_JSON))

        expect:
        cyberTrack.readAll(['SCP-1', 'SCP-2', 'SCP-3']) == [new CyberTrackTicket('SCP-1', 'To Do', URL + '/browse/SCP-1', null)]
    }

    def "no keys need no search and an empty answer finds nothing"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/search')).andRespond(withSuccess('{}', APPLICATION_JSON))

        expect:
        cyberTrack.readAll([]) == []
        cyberTrack.readAll(['SCP-1']) == []
    }

    def "a refused search is refused with the keys and the reason"() {
        given:
        server.expect(requestTo(URL + '/rest/api/2/search'))
                .andRespond(withBadRequest().body('{"errorMessages":["Error in the JQL Query"]}'))

        when:
        cyberTrack.readAll(['SCP-1', 'SCP-2'])

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('CyberTrack could not ' + CyberTrackAdapter.READ_ALL + ' SCP-1, SCP-2: 400 Bad Request')
    }

    private CyberTrackAdapter adapter(String projectKey, String issueType) {
        new CyberTrackAdapter(new CyberTrackProperties(URL + '/', 'secret-token', projectKey, issueType), builder)
    }
}
