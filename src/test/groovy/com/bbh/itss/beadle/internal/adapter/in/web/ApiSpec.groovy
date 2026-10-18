package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.adapter.ServiceTokenProperties
import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.calllog.port.in.CallLogUseCase
import com.bbh.itss.beadle.internal.application.confluence.port.in.ConfluenceUseCase
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackUseCase
import com.bbh.itss.beadle.internal.application.jira.port.in.JiraUseCase
import com.bbh.itss.beadle.internal.application.qtest.port.in.QTestUseCase
import com.bbh.itss.beadle.internal.application.status.port.in.StatusUseCase
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.domain.status.ServiceStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.CORRELATION_MAX
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.USER_MAX
import static com.bbh.itss.beadle.internal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.internal.domain.shared.Failures.staleVersion
import static com.bbh.itss.beadle.internal.domain.status.SystemStatus.of
import static com.bbh.itss.beadle.internal.support.Fixtures.callLogEntry
import static com.bbh.itss.beadle.internal.support.Fixtures.cycle
import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.issue
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.newPageJson
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdateJson
import static com.bbh.itss.beadle.internal.support.Fixtures.project
import static com.bbh.itss.beadle.internal.support.Fixtures.run
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static com.bbh.itss.beadle.internal.support.Fixtures.ticketJson
import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static com.bbh.itss.beadle.internal.support.Json.parse
import static com.bbh.itss.beadle.internal.support.Json.toJson
import static java.nio.charset.StandardCharsets.UTF_8
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/**
 * The REST API of Beadle-Internal as Beadle-GUI sees it: paths, methods, bodies, the service token, the caller
 * headers and the problems it answers, with the use cases stubbed.
 */
class ApiSpec extends Specification {

    static final String TOKEN = 'test-service-token'
    static final String TICKETS = '/api/internal/cybertrack/tickets'
    static final String PAGES = '/api/internal/confluence/pages'
    static final Instant NOON = Instant.parse('2026-10-15T12:00:00Z')
    static final Map PAGE = [id  : '100001', spaceKey: 'DSO', title: 'Beadle release notes', version: 3,
                             url : 'https://confluence.bbh.com/pages/viewpage.action?pageId=100001', body: '<p>Beadle 1.0</p>']
    static final Map ISSUE = [key: 'CERT-12', summary: 'Single sign-on with BBH SSO', status: 'In Progress', epicKey: null,
                              updated: '2026-10-01']

    JiraUseCase jira = Stub()
    CyberTrackUseCase cyberTrack = Stub()
    ConfluenceUseCase confluence = Stub()
    QTestUseCase qtest = Stub()
    CallLogUseCase callLog = Stub()
    StatusUseCase systems = Stub()
    List<Caller> callers = []
    MockMvc mvc = mvc(TOKEN)

    def setup() {
        systems.status() >> {
            callers << CallerContext.current()
            new ServiceStatus(of(false), of(true), of(false), of(true))
        }
        jira.versions('CERT') >> [version(), version(name: 'CERT 1.5', releaseDate: null)]
        jira.versions('NOPE') >> { throw new NoSuchElementException('Jira project NOPE does not exist') }
        jira.versions('DOWN') >> { throw new UncheckedIOException('Jira could not read the versions of DOWN: 503', new IOException()) }
        jira.versions('BOOM') >> { throw new IllegalStateException('at com.bbh') }
        jira.epics('CERT', 'CERT 1.4') >> [issue()]
        jira.epics('CERT', '') >> { throw InvalidRequestException.of('fixVersion', 'is required') }
        jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13']) >> [issue(key: 'CERT-20', epicKey: 'CERT-12', updated: null)]
        cyberTrack.create(ticket()) >> new CreatedTicket('SCP-1234', 'https://cybertrack.bbh.com/browse/SCP-1234')
        cyberTrack.read('SCP-1234') >> cyberTrackTicket()
        confluence.find('DSO', 'Beadle release notes') >> [page()]
        confluence.get('100001') >> page()
        confluence.create(newPage()) >> page(id: '100009', title: 'Change CHG0031001', version: 1, body: '<p>Evidence</p>')
        confluence.update('100001', pageUpdate()) >> page(version: 4, body: '<p>Beadle 1.1</p>')
        confluence.update('100001', pageUpdate(version: 2)) >> { throw staleVersion() }
        qtest.projects() >> [project()]
        qtest.cycles(1) >> [cycle()]
        qtest.runs(1, 10) >> [run(), run(id: 101L, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null)]
        qtest.summary(1, 10) >> new QTestSummary(42, 40, 1, 0, 1)
        callLog.find(new CallLogQuery('corr-1', NOON, NOON.plusSeconds(3600), 1, 2)) >> new CallLogPage(1, 2, 3, [callLogEntry()])
        callLog.find(new CallLogQuery(null, null, null, 0, 50)) >> new CallLogPage(0, 50, 0, [])
    }

    def "#method #path answers #status"() {
        when:
        def response = call(request)

        then:
        response.status == status
        response.contentType.startsWith('application/json')
        parse(response.contentAsString) == body

        where:
        request                                                                    || status | body
        get('/api/internal/status')                                                || 200    | [jira: [connected: false, mode: 'demo'], cyberTrack: [connected: true, mode: 'live'], confluence: [connected: false, mode: 'demo'], qtest: [connected: true, mode: 'live']]
        get('/api/internal/jira/projects/CERT/versions')                           || 200    | [[name: 'CERT 1.4', released: false, releaseDate: '2026-11-01'], [name: 'CERT 1.5', released: false, releaseDate: null]]
        get('/api/internal/jira/projects/CERT/epics?fixVersion=CERT 1.4')          || 200    | [ISSUE]
        get('/api/internal/jira/projects/CERT/stories?fixVersion=CERT 1.4&epicKeys=CERT-12,CERT-13')            || 200 | [ISSUE + [key: 'CERT-20', epicKey: 'CERT-12', updated: null]]
        get('/api/internal/jira/projects/CERT/stories?fixVersion=CERT 1.4&epicKeys=CERT-12&epicKeys=CERT-13')   || 200 | [ISSUE + [key: 'CERT-20', epicKey: 'CERT-12', updated: null]]
        json(post(TICKETS), ticketJson(changeNumber: ' CHG0031001 '))             || 201    | [key: 'SCP-1234', url: 'https://cybertrack.bbh.com/browse/SCP-1234']
        get(TICKETS + '/SCP-1234')                                                 || 200    | [key: 'SCP-1234', status: 'To Do', url: 'https://cybertrack.bbh.com/browse/SCP-1234', updated: '2026-10-15T09:00:00Z']
        get(PAGES + '?spaceKey=DSO&title=Beadle release notes')                   || 200    | [PAGE - [body: PAGE.body]]
        get(PAGES + '/100001')                                                     || 200    | PAGE
        json(post(PAGES), newPageJson(spaceKey: ' DSO '))                          || 201    | PAGE + [id: '100009', title: 'Change CHG0031001', version: 1, body: '<p>Evidence</p>']
        json(put(PAGES + '/100001'), pageUpdateJson())                             || 200    | PAGE + [version: 4, body: '<p>Beadle 1.1</p>']
        get('/api/internal/qtest/projects')                                        || 200    | [[id: 1, name: 'CertScanner']]
        get('/api/internal/qtest/projects/1/test-cycles')                          || 200    | [[id: 10, name: 'Release 1.4', pid: 'CL-10']]
        get('/api/internal/qtest/projects/1/test-runs?cycleId=10')                 || 200    | [[id: 100, name: 'Sign in with SSO', pid: 'TR-100', status: 'Passed', executedAt: '2026-10-15T09:00:00Z'], [id: 101, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null]]
        get('/api/internal/qtest/projects/1/test-cycles/10/summary')               || 200    | [total: 42, passed: 40, failed: 1, blocked: 0, unexecuted: 1]
        get('/api/internal/call-log')                                              || 200    | [page: 0, size: 50, total: 0, entries: []]
        get('/api/internal/call-log?correlationId=corr-1&from=2026-10-15T12:00:00Z&to=2026-10-15T13:00:00Z&page=1&size=2') || 200 | [page: 1, size: 2, total: 3, entries: [[id: 1, at: '2026-10-15T09:00:00Z', userName: 'Mateusz Matan', correlationId: 'corr-1', system: 'JIRA', operation: 'versions', target: 'CERT', outcome: 'SUCCESS', message: null, durationMs: 12]]]

        method = request.buildRequest(new MockServletContext()).method
        path = request.buildRequest(new MockServletContext()).requestURI
    }

    def "a created ticket and a created page are answered with their location"() {
        expect:
        call(json(post(TICKETS), ticketJson())).getHeader('Location') == 'http://localhost/api/internal/cybertrack/tickets/SCP-1234'
        call(json(post(PAGES), newPageJson())).getHeader('Location') == 'http://localhost/api/internal/confluence/pages/100009'
    }

    def "#problem is #status #title"() {
        when:
        def response = call(request)

        then:
        response.status == status
        response.contentType.startsWith('application/problem+json')
        with(parse(response.contentAsString)) {
            it.title == title
            it.detail == detail
            it.problems*.field == fields
        }

        where:
        problem                        | request                                                   || status | title                    | detail                                                                | fields
        'an unknown project'           | get('/api/internal/jira/projects/NOPE/versions')          || 404    | 'Not found'              | 'Jira project NOPE does not exist'                                    | null
        'an unreachable system'        | get('/api/internal/jira/projects/DOWN/versions')          || 502    | 'Bad Gateway'            | 'Jira could not read the versions of DOWN: 503'                       | null
        'an unexpected failure'        | get('/api/internal/jira/projects/BOOM/versions')          || 500    | 'Request failed'         | 'Beadle-Internal could not handle the request. The failure is in its log.' | null
        'a stale page update'          | json(put(PAGES + '/100001'), pageUpdateJson(version: 2))  || 409    | 'Conflict'               | STALE_VERSION                                                         | null
        'a missing FixVersion'         | get('/api/internal/jira/projects/CERT/epics')             || 400    | 'Validation failed'      | 'is required'                                                         | ['fixVersion']
        'a ticket without inputs'      | json(post(TICKETS), ticketJson(inputs: null))             || 400    | 'Validation failed'      | '4 fields are invalid'                                                | ['inputs.apoNumber', 'inputs.bitbucketUrl', 'inputs.artifactLink', 'inputs.qcApplicationLink']
        'a page without space and body' | json(post(PAGES), newPageJson(spaceKey: null, body: ' ')) || 400   | 'Validation failed'      | '2 fields are invalid'                                                | ['spaceKey', 'body']
        'a call log page of 0'         | get('/api/internal/call-log?size=0')                      || 400    | 'Validation failed'      | 'must be between 1 and 500'                                           | ['size']
        'a body that is no JSON'       | body(post(TICKETS), '{"changeNumber":')                   || 400    | 'Bad Request'            | 'Failed to read request'                                              | null
        'a project id that is no number' | get('/api/internal/qtest/projects/x/test-cycles')       || 400    | 'Bad Request'            | "Failed to convert 'id' with value: 'x'"                              | null
        'another method'               | post('/api/internal/status')                              || 405    | 'Method Not Allowed'     | "Method 'POST' is not supported."                                     | null
        'another content type'         | post(TICKETS).contentType('text/plain').content('CHG1')   || 415    | 'Unsupported Media Type' | "Content-Type 'text/plain' is not supported."                         | null
    }

    def "a call #without is 401 as a problem that names the token"() {
        when:
        def request = get(URI.create(path))
        def response = mvc.perform(authorization == null ? request : request.header('Authorization', authorization))
                .andReturn().response

        then:
        response.status == 401
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.getHeader('WWW-Authenticate') == 'Bearer'
        parse(response.contentAsString) == [title: 'Unauthorized', status: 401, detail: ServiceTokenFilter.DETAIL]
        callers == []

        where:
        without                    | path                                             | authorization
        'without a token'          | '/api/internal/status'                           | null
        'with another token'       | '/api/internal/jira/projects/CERT/versions'      | 'Bearer wrong'
        'with an empty bearer'     | '/api/internal/call-log'                         | 'Bearer '
        'without the Bearer word'  | TICKETS + '/SCP-1234'                            | TOKEN
        'with basic credentials'   | '/actuator/info'                                 | 'Basic dGVzdA=='
        'to a path like health'    | '/actuator/healthcheck'                          | null
        'past health with ..'      | '/actuator/health/../../api/internal/status'     | null
        'past health with %2e%2e'  | '/actuator/health/%2e%2e/%2e%2e/api/internal/status' | null
        'to health with a ;'       | '/actuator/health;x'                             | null
    }

    def "the health endpoints are open, and a blank service token refuses every other call rather than opening the API"() {
        given:
        def closed = mvc(token)

        expect:
        ['/actuator/health', '/actuator/health/liveness'].every { closed.perform(get(it)).andReturn().response.status != 401 }
        closed.perform(get('/api/internal/status').header('Authorization', 'Bearer ' + token)).andReturn().response.status == 401

        where:
        token << [null, ' ']
    }

    def "an accepted call runs as the user and correlation id of its headers, and the caller is cleared afterwards"() {
        when:
        call(get('/api/internal/status').header('X-Beadle-User', header).header('X-Correlation-Id', ' corr-1 '))
        call(get('/api/internal/status'))

        then:
        callers[0] == new Caller(user, 'corr-1')
        callers[1].user() == 'unknown'
        callers[1].correlationId() ==~ /[0-9a-f-]{36}/
        CallerContext.current() == Caller.NONE

        where:
        header                                       || user
        '%C5%81ukasz+W%C3%B3jcik'                    || 'Łukasz Wójcik'
        ' Mateusz Matan '                            || 'Mateusz Matan'
        '100% sure'                                  || '100% sure'
        '%C5%8'                                      || '%C5%8'
        '+++'                                        || 'unknown'
        URLEncoder.encode('Ł' * 150, UTF_8)          || 'Ł' * 98 + '...'
        'u' * 250                                    || 'u' * (USER_MAX - 3) + '...'
    }

    def "a correlation id of #length bytes is #outcome"() {
        when:
        def response = call(get('/api/internal/status').header('X-Correlation-Id', 'c' * length))

        then:
        response.status == code
        callers*.correlationId() == accepted

        where:
        length              || code | accepted
        CORRELATION_MAX     || 200  | ['c' * CORRELATION_MAX]
        CORRELATION_MAX + 1 || 400  | []
        outcome = code == 200 ? 'taken as it is' : 'refused, since cutting it would lose the callbacks of the action'
    }

    def "a correlation id too long is refused as a problem naming the header"() {
        when:
        def response = call(get('/api/internal/status').header('X-Correlation-Id', 'c' * 120))

        then:
        response.contentType == 'application/problem+json;charset=UTF-8'
        parse(response.contentAsString) == [title   : 'Validation failed', status: 400,
                                           detail  : 'X-Correlation-Id ' + ServiceTokenFilter.CORRELATION_TOO_LONG,
                                           problems: [[field: 'X-Correlation-Id', message: ServiceTokenFilter.CORRELATION_TOO_LONG]]]
    }

    private MockMvc mvc(String token) {
        standaloneSetup(new JiraController(jira), new CyberTrackController(cyberTrack), new ConfluenceController(confluence),
                new QTestController(qtest), new CallLogController(callLog), new StatusController(systems))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new ServiceTokenFilter(new ServiceTokenProperties(token)))
                .build()
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) {
        def response = mvc.perform(request.header('Authorization', 'Bearer ' + TOKEN)).andReturn().response
        response.characterEncoding = 'UTF-8'
        response
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        request.contentType(APPLICATION_JSON).content(toJson(body))
    }

    private static MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String body) {
        request.contentType(APPLICATION_JSON).content(body)
    }
}
