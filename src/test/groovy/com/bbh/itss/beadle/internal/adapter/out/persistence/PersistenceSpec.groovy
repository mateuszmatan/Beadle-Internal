package com.bbh.itss.beadle.internal.adapter.out.persistence

import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp
import liquibase.Liquibase
import liquibase.Scope
import liquibase.changelog.FastCheckService
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CONFLUENCE
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.internal.support.Fixtures.CREATED
import static com.bbh.itss.beadle.internal.support.Fixtures.callLogEntry
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

/**
 * The tables of Beadle-Internal on H2 in Oracle mode, built by the Liquibase changesets and validated by Hibernate as
 * on Oracle: the call log and the followed CyberTrack tickets.
 */
@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:persistence;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([CallLogPersistenceAdapter, TrackedTicketPersistenceAdapter])
class PersistenceSpec extends Specification {

    static final Instant LATER = Instant.parse('2026-10-15T09:30:00Z')
    static final Instant NOON = Instant.parse('2026-10-15T12:00:00Z')
    static final FollowUp FOLLOW_UP = new FollowUp(['Done'], 3)

    @Autowired
    CallLogPersistenceAdapter callLog

    @Autowired
    TrackedTicketPersistenceAdapter tickets

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    PlatformTransactionManager transactionManager

    def setup() {
        ['BEADLE_INTERNAL_CYBERTRACK_TICKET', 'BEADLE_INTERNAL_CALL_LOG'].each {
            jdbc.update("DELETE FROM $it")
        }
    }

    def "a followed ticket is written down and read back among the open ones, oldest first, until it is closed"() {
        when:
        tickets.track(trackedTicket(key: 'SCP-2', createdAt: CREATED.plusSeconds(60), misses: 2))
        tickets.track(trackedTicket())

        then:
        tickets.open() == [trackedTicket(), trackedTicket(key: 'SCP-2', createdAt: CREATED.plusSeconds(60), misses: 2)]
        jdbc.queryForMap("SELECT STATUS, CORRELATION_ID, MISSES, CHANGE_NUMBER FROM BEADLE_INTERNAL_CYBERTRACK_TICKET WHERE TICKET_KEY = 'SCP-1234'") ==
                [STATUS: 'To Do', CORRELATION_ID: 'corr-1', MISSES: 0, CHANGE_NUMBER: 'CHG0031001']

        when:
        tickets.save(trackedTicket().reported('Done', LATER, FOLLOW_UP))

        then:
        tickets.open()*.key() == ['SCP-2']
        tickets.find('SCP-1234').get().closedAt() == LATER
        tickets.find('SCP-9') == Optional.empty()
    }

    def "the open ticket of a change is the newest one not closed"() {
        given:
        tickets.track(trackedTicket(key: 'SCP-1', createdAt: CREATED.minusSeconds(60)))
        tickets.track(trackedTicket(key: 'SCP-2', createdAt: CREATED.plusSeconds(60)))
        tickets.track(trackedTicket(key: 'SCP-3', createdAt: CREATED.plusSeconds(120), closedAt: CREATED))

        expect:
        tickets.openFor('CHG0031001').get().key() == 'SCP-2'
        tickets.openFor('CHG0031002') == Optional.empty()
    }

    def "the highest key of a project is the one with the highest number, open or closed, whatever its prefix holds"() {
        given:
        keys.each { tickets.track(trackedTicket(key: it, closedAt: it == 'SCP-1000' ? CREATED : null)) }

        expect:
        tickets.highestKey(prefix) == Optional.ofNullable(highest)

        where:
        keys                              | prefix   || highest
        ['SCP-999', 'SCP-1000', 'SCP-12'] | 'SCP-'   || 'SCP-1000'
        ['SECX1-9', 'SEC_1-5']            | 'SEC_1-' || 'SEC_1-5'
        ['50X-9']                         | '50%-'   || null
    }

    def "a ticket that cannot be written down does not fail its creation, but fails a save of the poll"() {
        when:
        tickets.track(trackedTicket(key: 'SCP-' + '9' * 200))

        then:
        tickets.open() == []

        when:
        tickets.save(trackedTicket(createdAt: null))

        then:
        thrown(RuntimeException)
    }

    def "a change whose open ticket cannot be looked up has none, so that its ticket is created"() {
        given:
        jdbc.execute('ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET RENAME COLUMN CHANGE_NUMBER TO CHANGE_NO')

        expect:
        tickets.openFor('CHG0031001') == Optional.empty()

        cleanup:
        jdbc.execute('ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET RENAME COLUMN CHANGE_NO TO CHANGE_NUMBER')
    }

    def "a call log entry is written with a generated id, even when the transaction around it rolls back"() {
        when:
        def recorded = callLog.record(callLogEntry(id: null))
        def failed = new TransactionTemplate(transactionManager).execute { status ->
            def entry = callLog.record(callLogEntry(id: null, system: CONFLUENCE, operation: 'update', outcome: FAILURE,
                    message: 'Confluence could not update the page 100001: 409', durationMs: 345L))
            status.setRollbackOnly()
            entry
        }

        then:
        recorded == callLogEntry(id: recorded.id())
        failed.id() > recorded.id()
        callLog.find(CallLogQuery.of(null, null, null, 0, 10)).entries() == [failed, recorded]
    }

    def "the call log is found by #found, newest first, page by page"() {
        given:
        def entries = (0..<5).collect { index ->
            callLog.record(callLogEntry(id: null, at: NOON.plusSeconds(index * 60), correlationId: index % 2 ? 'odd' : 'even'))
        }

        expect:
        callLog.find(CallLogQuery.of(correlationId, from, to, page, 2)) == new CallLogPage(page, 2, total, expected.collect { entries[it] })

        where:
        found                         | correlationId | from                 | to                    | page || total | expected
        'page'                        | null          | null                 | null                  | 1    || 5     | [2, 1]
        'correlation id'              | 'even'        | null                 | null                  | 0    || 3     | [4, 2]
        'correlation id and window'   | 'odd'         | NOON.plusSeconds(60) | NOON.plusSeconds(180) | 0    || 1     | [1]
    }

    def "the call log refuses a row with #problem"() {
        when:
        jdbc.update('INSERT INTO BEADLE_INTERNAL_CALL_LOG (AT, SYSTEM, OPERATION, OUTCOME, DURATION_MS) VALUES (?, ?, ?, ?, 1)',
                NOON, system, 'read', outcome)

        then:
        thrown(DataIntegrityViolationException)

        where:
        problem              | system    | outcome
        'an unknown system'  | 'PROTECH' | 'SUCCESS'
        'an unknown outcome' | 'JIRA'    | 'PARTIAL'
        'no system'          | null      | 'SUCCESS'
    }

    def "every changeset rolls back cleanly and applies again"() {
        given:
        System.setProperty('liquibase.analytics.enabled', 'false')
        Connection connection = DriverManager.getConnection('jdbc:h2:mem:migration;MODE=Oracle;DB_CLOSE_DELAY=-1', 'sa', '')
        def liquibase = new Liquibase('db/changelog/db.changelog-master.yaml', new ClassLoaderResourceAccessor(),
                DatabaseFactory.instance.findCorrectDatabaseImplementation(new JdbcConnection(connection)))

        when:
        liquibase.update('')
        liquibase.rollback(6, '')
        Scope.currentScope.getSingleton(FastCheckService).clearCache()

        then:
        tables(connection) == ['DATABASECHANGELOG', 'DATABASECHANGELOGLOCK']

        when:
        liquibase.update('')

        then:
        tables(connection) == ['BEADLE_INTERNAL_CALL_LOG', 'BEADLE_INTERNAL_CYBERTRACK_TICKET', 'DATABASECHANGELOG',
                               'DATABASECHANGELOGLOCK']

        cleanup:
        liquibase?.close()
    }

    private static List<String> tables(Connection connection) {
        def found = connection.metaData.getTables(null, 'PUBLIC', '%', ['TABLE'] as String[])
        def names = []
        while (found.next()) {
            names << found.getString('TABLE_NAME')
        }
        names.sort()
    }
}
